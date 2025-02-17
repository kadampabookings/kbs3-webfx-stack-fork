package dev.webfx.stack.orm.entity;

import dev.webfx.stack.orm.domainmodel.DomainClass;
import dev.webfx.stack.orm.entity.impl.EntityIdImpl;

/**
 * Interface for a unique identifier designating an entity. An EntityId doesn't require it's designated Entity to be
 * also present in memory.
 *
 * @author Bruno Salmon
 */
public interface EntityId {

    /**
     * @return the domain class for the entity
     */
    DomainClass getDomainClass();

    /**
     * @return the primary key of the counterpart database record for this entity
     */
    Object getPrimaryKey();

    /**
     * @return true if the designated entity is not yet inserted in the database. In this case, the primary key is a
     * temporary but non-null object that works to identify the in-memory newly created entity instance.
     */
    boolean isNew();

    default EntityId refactor(Object newPrimaryKey) {
        return EntityId.create(getDomainClass(), newPrimaryKey);
    }

    static EntityId create(Object domainClassId, Object primaryKey) {
        return EntityIdImpl.create(domainClassId, primaryKey);
    }

    static EntityId create(Object domainClassId) {
        return EntityIdImpl.create(domainClassId);
    }

}
