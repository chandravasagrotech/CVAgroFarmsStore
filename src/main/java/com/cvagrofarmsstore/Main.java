package com.cvagrofarmsstore;

/**
 * Launcher entry point — kept separate from App to avoid JavaFX
 * "JavaFX runtime components are missing" error when running from JAR.
 */
public class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}
