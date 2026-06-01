package com.cvagrofarmsstore.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton Excel Database Manager.
 *
 * <p>The database file path is resolved dynamically at runtime via
 * {@link AppPreferences#getDbPath()} so the file is never tied to the
 * installation directory. All I/O runs on Java 21 Virtual Threads.
 * A {@link ReentrantLock} prevents concurrent workbook corruption.
 */
public final class ExcelDatabaseManager {

    private static final Logger LOG = LogManager.getLogger(ExcelDatabaseManager.class);

    // ── Required sheets that every valid DB must contain ─────────────────────
    private static final Set<String> REQUIRED_SHEETS =
            Set.of("Categories", "Products", "SalesLog");

    // ── Sheet / header definitions ────────────────────────────────────────────
    static final Map<String, String[]> SHEET_HEADERS = new LinkedHashMap<>();
    static {
        SHEET_HEADERS.put("Categories", new String[]{
                "Category ID", "Category Name", "Description"
        });
        SHEET_HEADERS.put("Products", new String[]{
                "Product ID", "Category ID", "Product Name",
                "Price", "Current Stock", "Minimum Alert Level", "Last Updated"
        });
        SHEET_HEADERS.put("SalesLog", new String[]{
                "Transaction ID", "Timestamp", "Product ID",
                "Quantity Sold", "Unit Price", "Total Amount"
        });
        SHEET_HEADERS.put("StockHistory", new String[]{
                "Product ID", "Date", "Stock Before", "Qty Added", "Stock After"
        });
    }

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static final class Holder {
        static final ExcelDatabaseManager INSTANCE = new ExcelDatabaseManager();
    }

    public static ExcelDatabaseManager getInstance() {
        return Holder.INSTANCE;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final ExecutorService virtualExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    private final ReentrantLock workbookLock = new ReentrantLock();
    private static final int LOCK_TIMEOUT_SECONDS = 10;

    private boolean tryAcquireLock() throws InterruptedException {
        return workbookLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ── Dynamic path resolution ───────────────────────────────────────────────

    /** Always resolves the current DB path from preferences — never cached. */
    private Path dbPath() {
        return AppPreferences.getDbPath();
    }

    private Path backupDir() {
        return AppPreferences.getBackupDir();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Validates that the file at {@code path} is a genuine CVAgroFarms workbook
     * by checking that all required sheets exist with their header rows.
     * Runs synchronously on the calling (virtual) thread — wrap in a
     * {@code CompletableFuture.supplyAsync} when calling from the FX thread.
     *
     * @return {@code null} on success, or an error message string on failure.
     */
    public static String validateImportedFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return "File not found: " + path;
        }
        try (FileInputStream fis = new FileInputStream(path.toFile());
             Workbook wb = new XSSFWorkbook(fis)) {

            for (String name : REQUIRED_SHEETS) {
                Sheet sheet = wb.getSheet(name);
                if (sheet == null) {
                    return "Missing required sheet: \"" + name + "\"";
                }
                Row header = sheet.getRow(0);
                if (header == null || header.getLastCellNum() < 1) {
                    return "Sheet \"" + name + "\" has no header row.";
                }
            }
            return null; // valid
        } catch (Exception e) {
            LOG.warn("Validation failed for {}: {}", path, e.getMessage());
            return "Could not read file: " + e.getMessage();
        }
    }

    /**
     * Checks for the DB file and creates / validates it, returning a
     * {@link CompletableFuture} that completes when initialization is done.
     * Safe to call from the JavaFX thread.
     */
    public CompletableFuture<Void> initializeAndGetFuture() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!tryAcquireLock()) {
                    throw new RuntimeException("Could not acquire lock for initialization — timed out");
                }
                try {
                    Path db = dbPath();
                    if (Files.exists(db)) {
                        LOG.info("Database found at: {}", db.toAbsolutePath());
                        validateSheets();
                    } else {
                        LOG.info("Database not found — creating new workbook at: {}",
                                db.toAbsolutePath());
                        createNewWorkbook();
                    }
                } catch (IOException e) {
                    LOG.error("Failed to initialize database", e);
                    throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
                } finally {
                    workbookLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Initialization interrupted", e);
            }
        }, virtualExecutor);
    }

    /**
     * Checks for the DB file and creates / validates it.
     * Safe to call from the JavaFX thread.
     */
    public void initializeAsync() {
        virtualExecutor.submit(() -> {
            try {
                if (!tryAcquireLock()) {
                    LOG.error("Could not acquire lock for initialization — timed out");
                    return;
                }
                try {
                    Path db = dbPath();
                    if (Files.exists(db)) {
                        LOG.info("Database found at: {}", db.toAbsolutePath());
                        validateSheets();
                    } else {
                        LOG.info("Database not found — creating new workbook at: {}",
                                db.toAbsolutePath());
                        createNewWorkbook();
                    }
                } catch (IOException e) {
                    LOG.error("Failed to initialize database", e);
                } finally {
                    workbookLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Initialization interrupted", e);
            }
        });
    }

    /**
     * Backs up the workbook to the {@code backups/} sibling folder and shuts
     * down the virtual-thread executor. Called from {@link com.cvagrofarmsstore.App#stop()}.
     */
    public void backupAndShutdown() {
        virtualExecutor.submit(() -> {
            try {
                if (!tryAcquireLock()) {
                    LOG.error("Could not acquire lock for backup — timed out");
                    return;
                }
                try {
                    performBackup();
                } catch (IOException e) {
                    LOG.error("Backup failed during shutdown", e);
                } finally {
                    workbookLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Backup interrupted", e);
            }
        });
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                LOG.warn("Executor did not terminate cleanly within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Shutdown interrupted", e);
        }
    }

    /** Submits an async write task. UI updates must be wrapped in {@code Platform.runLater}. */
    public CompletableFuture<Void> writeAsync(WorkbookTask task) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!tryAcquireLock()) {
                    LOG.error("Could not acquire lock for write — timed out");
                    return;
                }
                try (Workbook wb = openWorkbook()) {
                    task.execute(wb);
                    saveWorkbook(wb);
                } catch (IOException e) {
                    LOG.error("Async write I/O failed", e);
                } catch (Exception e) {
                    LOG.error("Async write task failed", e);
                } finally {
                    workbookLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Write task interrupted", e);
            }
        }, virtualExecutor);
    }

    /** Submits an async read task and returns a {@link CompletableFuture} with the result. */
    public <T> CompletableFuture<T> readAsync(WorkbookQuery<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!tryAcquireLock()) {
                    LOG.error("Could not acquire lock for read — timed out");
                    return null;
                }
                try (Workbook wb = openWorkbook()) {
                    return query.execute(wb);
                } catch (IOException e) {
                    LOG.error("Async read I/O failed", e);
                    return null;
                } catch (Exception e) {
                    LOG.error("Async read task failed", e);
                    return null;
                } finally {
                    workbookLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Read task interrupted", e);
                return null;
            }
        }, virtualExecutor);
    }

    // ── Functional interfaces ─────────────────────────────────────────────────

    @FunctionalInterface
    public interface WorkbookTask {
        void execute(Workbook workbook) throws Exception;
    }

    @FunctionalInterface
    public interface WorkbookQuery<T> {
        T execute(Workbook workbook) throws Exception;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void createNewWorkbook() throws IOException {
        Path db = dbPath();
        Files.createDirectories(db.getParent());
        try (Workbook wb = new XSSFWorkbook()) {
            SHEET_HEADERS.forEach((sheetName, headers) -> {
                Sheet sheet = wb.createSheet(sheetName);
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }
                LOG.info("Created sheet '{}' with {} columns", sheetName, headers.length);
            });
            saveWorkbook(wb);
        }
        LOG.info("New workbook created at: {}", db.toAbsolutePath());
    }

    private void validateSheets() throws IOException {
        try (Workbook wb = openWorkbook()) {
            boolean modified = false;
            for (Map.Entry<String, String[]> entry : SHEET_HEADERS.entrySet()) {
                if (wb.getSheet(entry.getKey()) == null) {
                    Sheet sheet = wb.createSheet(entry.getKey());
                    Row headerRow = sheet.createRow(0);
                    String[] headers = entry.getValue();
                    for (int i = 0; i < headers.length; i++) {
                        headerRow.createCell(i).setCellValue(headers[i]);
                    }
                    LOG.warn("Missing sheet '{}' was recreated.", entry.getKey());
                    modified = true;
                }
            }
            if (modified) saveWorkbook(wb);
        }
    }

    private void performBackup() throws IOException {
        Path db = dbPath();
        if (!Files.exists(db)) {
            LOG.warn("No database file found to back up.");
            return;
        }
        Path bDir = backupDir();
        Files.createDirectories(bDir);
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", java.util.Locale.ROOT));
        Path destination = bDir.resolve("CVAgroFarms_DB_" + timestamp + ".xlsx");
        Files.copy(db, destination, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Backup saved to: {}", destination.toAbsolutePath());
    }

    private Workbook openWorkbook() throws IOException {
        try (FileInputStream fis = new FileInputStream(dbPath().toFile())) {
            return new XSSFWorkbook(fis);
        }
    }

    private void saveWorkbook(Workbook wb) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dbPath().toFile())) {
            wb.write(fos);
        }
    }
}
