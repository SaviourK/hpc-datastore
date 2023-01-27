/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.security;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnauthenticatedAccessExceptionMapper implements
	ExceptionMapper<UnauthenticatedAccessException>
{

	@Override
	public Response toResponse(UnauthenticatedAccessException exception) {
		return Response.status(Status.FORBIDDEN).entity(String.format(
			"Anonymous user cannot access this resource"))
			.build();
	}

}
