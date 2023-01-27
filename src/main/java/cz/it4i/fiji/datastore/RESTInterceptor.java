/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore;

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import cz.it4i.fiji.datastore.timout_shutdown.TimeoutTimer;
import jakarta.annotation.Priority;
import lombok.extern.log4j.Log4j2;

@Log4j2
@TimeoutingRequest
@Interceptor
@Priority(1)
public class RESTInterceptor {

	@Inject
	TimeoutTimer timer;


	@AroundInvoke
	public Object intercept(InvocationContext ctx) throws Exception {
		log.debug("intercept: {}", ctx.getMethod());
		timer.scheduleTimer();
		return ctx.proceed();
	}
}
