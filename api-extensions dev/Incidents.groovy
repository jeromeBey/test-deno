package com.smartwavesa.rest.api;


import java.util.logging.Logger

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.bonitasoft.engine.api.ProcessAPI
import org.bonitasoft.engine.api.*
import org.bonitasoft.engine.identity.*
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfoSearchDescriptor
import org.bonitasoft.engine.bpm.process.ProcessInstance
import org.bonitasoft.engine.identity.UserNotFoundException
import org.bonitasoft.engine.search.Order
import org.bonitasoft.engine.search.SearchOptions
import org.bonitasoft.engine.search.SearchOptionsBuilder
import org.bonitasoft.engine.search.SearchResult
import org.bonitasoft.web.extension.rest.RestAPIContext
import org.bonitasoft.web.extension.rest.RestApiController
import org.bonitasoft.web.extension.rest.RestApiResponse
import org.bonitasoft.web.extension.rest.RestApiResponseBuilder
import org.json.JSONObject

import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.JWTParser

import groovy.json.JsonBuilder

class Incidents implements RestApiController {

	private static final Logger LOGGER = Logger.getLogger("org.bonitasoft")

	@Override
	RestApiResponse doHandle(HttpServletRequest request, RestApiResponseBuilder responseBuilder, RestAPIContext context) {
		try {
			
			LOGGER.fine("API BEGIN")
			String jwtHeader = request.getHeader("x-m3-requester-id")
			LOGGER.fine(jwtHeader)
			if (jwtHeader == null) {
				return buildResponse(responseBuilder, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"""{"error" : "invalid JWT"}""")
			}

			JWT jwtParse = JWTParser.parse(jwtHeader);
			JWTClaimsSet claim = jwtParse.getJWTClaimsSet();
			String email = claim.getClaim("email").toString();
			LOGGER.fine(email)
				
			String joinRequest = request.reader.readLines().join("");
			LOGGER.fine(joinRequest)

			JSONObject obj = new JSONObject(joinRequest);
			JSONObject incidentJSON =  obj.getJSONObject("incident")
			JSONObject typeIncidentJSON =  obj.getJSONObject("typeIncident")
	
	
			Map<String,Serializable> instantiationInputs = new HashMap();
			def userId = null
			

			IdentityAPI identityAPI = context.getApiClient().getIdentityAPI()
			SearchResult<User> searchUsers = identityAPI.searchUsers(new SearchOptionsBuilder(0, 1000000).done());
			for (final User user : searchUsers.getResult()) {
				if (user==null){ 
					Exit
				}
				UserWithContactData proUser = identityAPI.getUserWithProfessionalDetails(user.getId())
				ContactData cd = proUser.getContactData()
				if (cd!=null){ 
					String aEmail = cd.getEmail()
					if (email.equals(aEmail)){
						userId=user.getId()
					}
				}
			}
			
			//UserId is not found
			if (userId.equals(null)){
				return buildResponse(responseBuilder, HttpServletResponse.SC_UNAUTHORIZED,"""{"error" : "Invalid email"}""")
			}
			
			Map<String,Serializable> incidentInput =	incidentJSON.toMap()
			Map<String,Serializable> typeIncidentInput = typeIncidentJSON.toMap()

			instantiationInputs.put("incidentInput",incidentInput)
			instantiationInputs.put("typeIncidentInput",typeIncidentInput)

			ProcessAPI processAPI = context.getApiClient().getProcessAPI()

			final SearchOptions searchOptions = new SearchOptionsBuilder(0, 100).searchTerm("IncidentLocataire").sort(ProcessDeploymentInfoSearchDescriptor.VERSION, Order.DESC).done();

			final SearchResult<ProcessDeploymentInfo> deploymentInfoResults = processAPI.searchProcessDeploymentInfos(searchOptions);

			
			ProcessInstance processInstance = processAPI.startProcessWithInputs(userId,deploymentInfoResults.result.get(0).processId, instantiationInputs)

            def caseId = processInstance.getId().toString()
            def builder = new JsonBuilder()
            builder.incident {
                id caseId
            }
            
            return buildResponse(responseBuilder, HttpServletResponse.SC_OK, builder.toString())

		}
		catch (UserNotFoundException userNorFoundException) {
			LOGGER.severe(userNorFoundException.getMessage())
			return buildResponse(responseBuilder, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"""{"error" : "locataire not found"}""")
		}
	}

	/**
	 * Build an HTTP response.
	 *
	 * @param  responseBuilder the Rest API response builder
	 * @param  httpStatus the status of the response
	 * @param  body the response body
	 * @return a RestAPIResponse
	 */
	RestApiResponse buildResponse(RestApiResponseBuilder responseBuilder, int httpStatus, Serializable body) {
		return responseBuilder.with {
			withResponseStatus(httpStatus)
			withResponse(body)
			build()
		}
	}

	/**
	 * Returns a paged result like Bonita BPM REST APIs.
	 * Build a response with a content-range.
	 *
	 * @param  responseBuilder the Rest API response builder
	 * @param  body the response body
	 * @param  p the page index
	 * @param  c the number of result per page
	 * @param  total the total number of results
	 * @return a RestAPIResponse
	 */
	RestApiResponse buildPagedResponse(RestApiResponseBuilder responseBuilder, Serializable body, int p, int c, long total) {
		return responseBuilder.with {
			withContentRange(p,c,total)
			withResponse(body)
			build()
		}
	}
}
