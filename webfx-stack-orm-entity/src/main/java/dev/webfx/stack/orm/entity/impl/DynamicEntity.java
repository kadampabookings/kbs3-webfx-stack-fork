package dev.webfx.stack.orm.entity.impl;


import dev.webfx.stack.orm.entity.Entity;
import dev.webfx.stack.orm.entity.EntityId;
import dev.webfx.stack.orm.entity.EntityStore;
import dev.webfx.stack.orm.entity.UpdateStore;
import dev.webfx.platform.console.Console;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Bruno Salmon
 */
public class DynamicEntity implements Entity {

    private EntityId id;
    private final EntityStore store;
    private final Map<Object, Object> fieldValues = new HashMap<>();

    protected DynamicEntity(EntityId id, EntityStore store) {
        this.id = id;
        this.store = store;
    }

    @Override
    public EntityId getId() {
        return id;
    }

    void refactorId(EntityId oldId, EntityId newId) {
        if (id.equals(oldId))
            id = newId;
        for (Map.Entry entry : fieldValues.entrySet())
            if (oldId.equals(entry.getValue()))
                entry.setValue(newId);
    }

    @Override
    public EntityStore getStore() {
        return store;
    }

    @Override
    public Object getFieldValue(Object domainFieldId) {
        return fieldValues.get(domainFieldId);
    }

    @Override
    public boolean isFieldLoaded(Object domainFieldId) {
        return fieldValues.containsKey(domainFieldId);
    }

    @Override
    public void setForeignField(Object foreignFieldId, Object foreignFieldValue) {
        EntityId foreignEntityId;
        if (foreignFieldValue == null)
            foreignEntityId = null;
        else if (foreignFieldValue instanceof EntityId)
            foreignEntityId = (EntityId) foreignFieldValue;
        else if (foreignFieldValue instanceof Entity) {
            Entity entity = (Entity) foreignFieldValue;
            foreignEntityId = entity.getId();
            if (entity.getStore() != store && store.getEntity(foreignEntityId) == null) {
                store.copyEntity(entity);
                Console.log("Warning: this foreign entity has been copied into the store otherwise it was not accessible: " + entity);
            }
        } else {
            Object foreignClass = getDomainClass().getForeignClass(foreignFieldId);
            foreignEntityId = getStore().getEntityId(foreignClass, foreignFieldValue);
        }
        setFieldValue(foreignFieldId, foreignEntityId);
    }

    @Override
    public EntityId getForeignEntityId(Object foreignFieldId) {
        Object value = getFieldValue(foreignFieldId);
        if (value instanceof EntityId)
            return (EntityId) value;
        return null;
    }

    @Override
    public void setFieldValue(Object domainFieldId, Object value) {
        if (store instanceof UpdateStore) {
            Object previousValue = fieldValues.get(domainFieldId);
            ((UpdateStoreImpl) this.store).updateEntity(id, domainFieldId, value, previousValue);
        }
        fieldValues.put(domainFieldId, value);
    }

    @Override
    public String toString() {
        return toString(new StringBuilder()).toString();
    }

    public StringBuilder toString(StringBuilder sb) {
        sb.append(id.getDomainClass()).append("(pk: ").append(id.getPrimaryKey());
        for (Map.Entry<?, ?> entry : fieldValues.entrySet())
            sb.append(", ").append(entry.getKey()).append(": ").append(entry.getValue());
        sb.append(')');
        return sb;
    }

    public void copyAllFieldsFrom(Entity entity) {
        DynamicEntity dynamicEntity = (DynamicEntity) entity;
        fieldValues.putAll(dynamicEntity.fieldValues);
    }

    // Implementing equals() and hashCode() -- Ex: entities are used as keys in GanttLayout parent/grandparent cache

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicEntity that = (DynamicEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
