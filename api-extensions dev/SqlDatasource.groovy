import groovy.json.JsonBuilder
import groovy.sql.Sql
import org.bonitasoft.console.common.server.page.*

import javax.naming.Context
import javax.naming.InitialContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.sql.DataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class SqlDatasource implements RestApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger("org.bonitasoft")

    @Override
    RestApiResponse doHandle(HttpServletRequest httpServletRequest, PageResourceProvider pageResourceProvider, PageContext pageContext, RestApiResponseBuilder restApiResponseBuilder, RestApiUtil restApiUtil) {

        String queryId = httpServletRequest.getParameter "queryId"
        if (queryId == null) {
            return buildResponse(restApiResponseBuilder, "the parameter queryId is missing")
        }
        String query = getQuery queryId, pageResourceProvider
        if (query == null) {
            return buildResponse(restApiResponseBuilder, "{message: the queryId " + queryId + " does not refer to an existing query (SqlDatasource)}")
        }
        Map<String, String> params = getSqlParameters httpServletRequest
        Sql sql = buildSql pageResourceProvider
        try {
            def rows = params.isEmpty() ? sql.rows(query) : sql.rows(query, params)
            JsonBuilder builder = new JsonBuilder(rows)
            String table = builder.toPrettyString()
            return buildResponse(restApiResponseBuilder, table)
        } catch (Exception e) {
            if(!e.getMessage().startsWith("ERROR: invalid input syntax for integer")) {
                LOGGER.error("Exception when loading ressource " + e.getMessage(), e.getMessage());
                LOGGER.error("Query in error: " + queryId + " - " + query);
            }
        } finally {
            sql.close()
        }
        return buildResponse(restApiResponseBuilder, "");
    }

    protected RestApiResponse buildErrorResponse(RestApiResponseBuilder apiResponseBuilder, String message, Logger logger) {
        logger.severe message

        Map<String, String> result = [:]
        result.put "error", message
        apiResponseBuilder.withResponseStatus(HttpServletResponse.SC_BAD_REQUEST)
        buildResponse apiResponseBuilder, result
    }

    protected RestApiResponse buildResponse(RestApiResponseBuilder apiResponseBuilder, Serializable result) {
        apiResponseBuilder.with {
            withResponse(result)
            build()
        }
    }

    protected Map<String, String> getSqlParameters(HttpServletRequest request) {
        Map<String, String> params = [:]
        for (String parameterName : request.getParameterNames()) {
            params.put(parameterName, request.getParameter(parameterName))
        }
        params.remove("queryId")
        params
    }

    protected Sql buildSql(PageResourceProvider pageResourceProvider) {
        Properties props = loadProperties "datasource.properties", pageResourceProvider
        Context ctx = new InitialContext(props)
        DataSource dataSource = (DataSource) ctx.lookup(props["datasource.name"])
        new Sql(dataSource)
    }

    protected String getQuery(String queryId, PageResourceProvider pageResourceProvider) {
        Properties props = loadProperties "queries.properties", pageResourceProvider
        props[queryId]
    }

    protected Properties loadProperties(String fileName, PageResourceProvider pageResourceProvider) {
        Properties props = new Properties()
        pageResourceProvider.getResourceAsStream(fileName).withStream {
            InputStream s -> props.load s
        }
        props
    }


}

