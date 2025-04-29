package com.goodmem.security;

import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;

public abtract class DefaultRoleImpl implements Role {

    private Set<Permission> permissions;

    public DefaultRoleImpl(Permission ... permissions) {
        this.permissions = Sets.newHashSet(permissions);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("description", getDescription())
                .add("permissionCount", permissions.size())
                .toString();
    }
}
