package com.cvagrofarmsstore.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Category {

    private final StringProperty id          = new SimpleStringProperty();
    private final StringProperty name        = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();

    public Category() {}

    public Category(String id, String name, String description) {
        this.id.set(id);
        this.name.set(name);
        this.description.set(description);
    }

    public StringProperty idProperty()          { return id; }
    public StringProperty nameProperty()        { return name; }
    public StringProperty descriptionProperty() { return description; }

    public String getId()          { return id.get(); }
    public String getName()        { return name.get(); }
    public String getDescription() { return description.get(); }

    public void setId(String v)          { id.set(v); }
    public void setName(String v)        { name.set(v); }
    public void setDescription(String v) { description.set(v); }
}
