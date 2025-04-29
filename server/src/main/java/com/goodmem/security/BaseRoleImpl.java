package com.goodmem.security;

import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;

public abstract class BaseRoleImpl implements Role {

  private final Set<Permission> permissions;

  public BaseRoleImpl(Permission... permissions) {
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
