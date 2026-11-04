package com.tenderops.security;

/** Thrown on cross-company access; mapped to 404 so resource existence does not leak. */
public class ResourceNotVisibleException extends RuntimeException {
  public ResourceNotVisibleException() {
    super("Not found");
  }
}
