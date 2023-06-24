package cz.it4i.fiji.datastore.filter;

import lombok.extern.log4j.Log4j2;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

@Log4j2
// @Provider
// Enable if needed
public class RequestLoggingFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Log the request details
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        String queryString = requestContext.getUriInfo().getRequestUri().getQuery();
        log.trace("method: {}, path {}, queryString {}", method, path, queryString);
    }
}
