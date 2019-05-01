/*-
 * Copyright (c) 2016-2019   KTH Royal Institute of Technology.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 * Omar Kacimi         -  Initial implementation
 * Andrew Berezovskyi  -  Lyo contribution updates; refactoring.
 */

package org.eclipse.lyo.oslc4j.trs.server.service;

import java.net.URI;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.lyo.core.trs.Base;
import org.eclipse.lyo.core.trs.ChangeLog;
import org.eclipse.lyo.core.trs.Page;
import org.eclipse.lyo.core.trs.TRSConstants;
import org.eclipse.lyo.core.trs.TrackedResourceSet;
import org.eclipse.lyo.oslc4j.core.annotation.OslcService;
import org.eclipse.lyo.oslc4j.core.model.Error;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;
import org.eclipse.lyo.oslc4j.trs.server.PagedTrs;
import org.eclipse.lyo.oslc4j.trs.server.TRSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The service class for the TRS interface. This class needs to be implemented by an OSLC adapter
 * wishing to implement a TRS interface
 *
 * @version $version-stub$
 * @since 2.3.0
 */
@Path("/trs")
@OslcService(TRSConstants.TRS_NAMESPACE)
public class TrackedResourceSetService {
    private static final Logger log     = LoggerFactory.getLogger(TrackedResourceSetService.class);
    private static final String BASE_PATH = "base";
    private static final String CHANGELOG_PATH = "changeLog";

    /**
     * The instance of the change histories class used by a trs service class implementing this
     * class. The instance returned is expected to be a singleton of a class implementing the
     * ChangeHistories class
     */
    private PagedTrs changeHistories;

    public TrackedResourceSetService() {
    }

    @Inject
    public TrackedResourceSetService(PagedTrs _changeHistories) {
        changeHistories = _changeHistories;
    }

    /**
     * the method managing calls asking for the tracked resource set object.
     *
     * @return the tracked resource set representation
     */
    @GET
    @Produces({OslcMediaType.TEXT_TURTLE, OslcMediaType.APPLICATION_RDF_XML,
            OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON})
    public TrackedResourceSet getTrackedResourceSet(@Context UriInfo uriInfo)
            throws URISyntaxException {
        TrackedResourceSet result = new TrackedResourceSet();

        result.setAbout(uriInfo.getRequestUri());
        result.setBase(uriInfo.getAbsolutePathBuilder().path(BASE_PATH).build());

        if(getPagedTrs().changelogPageCount() == 0) {
            // FIXME Andrew@2019-04-27: remove this exception from the signature
            result.setChangeLog(new ChangeLog());
        } else {
            result.setChangeLog(getPagedTrs().getChangeLogLast());
        }

        return result;
    }

    /**
     * manage http calls for the first page of the base. The call is redirected to the handler of
     * http calls for a specific base page as a call for the page 1 of the base
     *
     * @return the first page of the base
     */
    @GET
    @Path(BASE_PATH)
    public Response getBase(@Context UriInfo uriInfo) {
        final URI newURI = uriInfo.getAbsolutePathBuilder().path("1").build();
        return Response.seeOther(newURI).build();
    }

    /**
     * manage calls for a specific page of the base
     *
     * @param pageNo the requested page of the base
     * @return the requested page of the base
     */
    @GET
    @Path(BASE_PATH + "/{page}")
    @Produces({OslcMediaType.TEXT_TURTLE, OslcMediaType.APPLICATION_RDF_XML,
                      OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON})
    public Response getBasePage(@PathParam("page") int pageNo) {
        Base base = getPagedTrs().getBaseResource(pageNo);
        if (base == null) {
            final Error entity = new Error();
            entity.setMessage("Wrong TRS Base page URI");
            entity.setStatusCode(String.valueOf(Status.NOT_FOUND.getStatusCode()));
            return Response.status(Status.NOT_FOUND).entity(entity).build();
        }

        // Return the nextPage Page object, which describes the next base page in terms,
        // of the current base page we are manipulating.  We do not directly 
        // return the base object due to a limitation in OSLC4J.  Currently 
        // OSLC4J requires that triples in the RDF graph with different subjects
        // reference one another.  According to the 2.0 spec, the Page object
        // already references the Base object so we will get the appropriate
        // output if we return Page.  If we force a reference from Base to Page
        // instead then we get a ldp:nextPage entry which does not conform to the
        // TRS 2.0 specification.
        // TODO Andrew@2019-05-01: fix it
        Page nextPage = base.getNextPage();
        if (nextPage == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        log.debug("TRS Base page contains {} members", base.getMembers().size());
        return Response.ok(base).header("Link", TRSUtil.linkHeaderValue(base)).build();
    }

    protected PagedTrs getPagedTrs() {
        return changeHistories;
    }

    /**
     * manage the calls for the change log and redirects to the handler of a specific page of the
     * change log with the call to the first page
     *
     * @return the first page of the change lof
     */
    @GET
    @Path(CHANGELOG_PATH)
    public Response getChangeLog(@Context UriInfo uriInfo) {
        final URI newURI = uriInfo.getAbsolutePathBuilder().path("1").build();
        return Response.seeOther(newURI).build();
    }


    /**
     * Returns the requested page of the change log
     *
     * @param page the page number of the wanted page
     * @return the requested page of the change log
     */
    @GET
    @Path(CHANGELOG_PATH + "/{page}")
    @Produces({OslcMediaType.TEXT_TURTLE, OslcMediaType.APPLICATION_RDF_XML,
                      OslcMediaType.APPLICATION_XML, OslcMediaType.APPLICATION_JSON})
    public Response getChangeLogPage(@PathParam("page") int page) {
        log.trace("TRS Change Log page '{}' requested", page);

        ChangeLog changeLog = getPagedTrs().getChangeLog(page);
        if (changeLog == null) {
            final Error entity = new Error();
            entity.setMessage("Wrong TRS Change Log page URI");
            entity.setStatusCode(String.valueOf(Status.NOT_FOUND.getStatusCode()));
            return Response.status(Status.NOT_FOUND).entity(entity).build();
        }
        log.debug("TRS Change Log page contains {} members", changeLog.getChange().size());
        return Response.ok(changeLog).build();
    }
}
