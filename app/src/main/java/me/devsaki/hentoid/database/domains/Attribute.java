package me.devsaki.hentoid.database.domains;

import com.google.gson.annotations.Expose;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;

import io.objectbox.annotation.Backlink;
import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;
import io.objectbox.relation.ToMany;
import me.devsaki.hentoid.enums.AttributeType;

/**
 * Created by DevSaki on 09/05/2015.
 * Attribute builder
 */
@Entity
public class Attribute {

    private final static int ATTRIBUTE_FILE_VERSION = 1;

    @Id(assignable = true) // Attributes are master data; primary key are name and type
    private long id;
    @Expose
    private final String url;
    @Expose
    private final String name;
    @Expose
    @Convert(converter = AttributeType.AttributeTypeConverter.class, dbType = Integer.class)
    private AttributeType type;
    // Runtime attributes; no need to expose them nor to persist them
    @Transient
    private int count;
    @Transient
    private int externalId = 0;
    @Backlink(to = "attributes") // backed by the to-many relation in Content
    public ToMany<Content> contents;


    public Attribute(AttributeType type, String name, String url) {
        this.type = type;
        this.name = name;
        this.url = url;
        this.id = (type.getCode() + "." + name).hashCode();
    }

    public Attribute(DataInputStream input) throws IOException {
        input.readInt(); // file version
        url = input.readUTF();
        name = input.readUTF();
        type = AttributeType.searchByCode(input.readInt());
        count = input.readInt();
        externalId = input.readInt();
        id = (type.getCode() + "." + name).hashCode();
    }


    public long getId() {
        return (0 == externalId) ? this.id : this.externalId;
    }

    public void setId(long id) { // Required for ObjectBox to compile even though id is final
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name.toLowerCase();
    }

    public AttributeType getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public Attribute setCount(int count) {
        this.count = count;
        return this;
    }

    public Attribute setExternalId(int id) {
        this.externalId = id;
        return this;
    }

    @Override
    public String toString() {
        return getName();
    }

    public void saveToStream(DataOutputStream output) throws IOException {
        output.writeInt(ATTRIBUTE_FILE_VERSION);
        output.writeUTF(null == url ? "" : url);
        output.writeUTF(name);
        output.writeInt(type.getCode());
        output.writeInt(count);
        output.writeInt(externalId);
    }

    public static final Comparator<Attribute> NAME_COMPARATOR = (a, b) -> a.getName().compareTo(b.getName());

    public static final Comparator<Attribute> COUNT_COMPARATOR = (a, b) -> {
        return Long.compare(a.getCount(), b.getCount()) * -1; /* Inverted - higher count first */
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attribute attribute = (Attribute) o;

        if ((externalId != 0 && attribute.externalId != 0) && externalId != attribute.externalId)
            return false;
        if (!name.equals(attribute.name)) return false;
        return type == attribute.type;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + externalId;
        return result;
    }
}
