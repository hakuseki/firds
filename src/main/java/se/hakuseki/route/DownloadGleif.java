package se.hakuseki.route;

import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.jdbc.JdbcOutputType;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * The type Gleif download.
 */
public class DownloadGleif extends EndpointRouteBuilder {
    /**
     * The constant GLEIF.
     */
    public static final String GLEIF = "GLEIF";


    /**
     * Configure.
     *
     * @throws Exception the exception
     */
    @Override
    public void configure() throws Exception {
        errorHandler(defaultErrorHandler());

        //tag::GLEIF[]
        from(activemq("gleif.lei").selector("JMSType='LEI'"))
                .routeId("GLEIF-Lookup").description("Queries GLEIF for a Jurisdiction of the Issuer using a LEI as argument")
                .autoStartup("{{firds.gleif.lei.startup}}")
                .log("Starting to update ISIN using LEI from Issuer")
                .to(direct(GLEIF))
                .log("GLEIF download completed!")
                .end();

        from(direct(GLEIF))
                .routeId(GLEIF)
                .setBody(constant("{{firds.gleif.lei.firds-select}}"))
                .to(jdbc("default").outputType(JdbcOutputType.SelectList))
                .split(body())
                .streaming()
                .parallelProcessing(true)
                .process(exchange -> {
                    final Message             in     = exchange.getIn();
                    final Map<String, String> body   = in.getBody(Map.class);
                    final String              issuer = body.get("issuer");
                    in.setBody(issuer);
                    in.setHeader("issuer", issuer);
                })
                .to(activemq("gleif.lei"))
                .end();

        from(activemq("gleif"))
                .routeId("GLEIF-AMQ")
                .errorHandler(deadLetterChannel("activemq:gleif.lei").useOriginalMessage()
                                                                     .disableRedelivery()
                                                                     .logExhaustedMessageBody(true)
                                                                     .logExhaustedMessageHistory(true)
                                                                     .logHandled(true)
                                                                     .log("GLEIF-AMQ")
                                                                     .onPrepareFailure(exchange -> {
                                                                         final Exception cause = exchange.getProperty(
                                                                                 Exchange.EXCEPTION_CAUGHT,
                                                                                 Exception.class);
                                                                         final Message in = exchange
                                                                                 .getIn();
                                                                         in.setHeader("FailedBecause",
                                                                                      cause.getMessage());
                                                                         in.setHeader(ScheduledMessage.AMQ_SCHEDULED_DELAY,
                                                                                      90_000L);
                                                                     }))
                .throttle(simple("{{firds.gleif.lei.throttle.requests}}",Long.class))
                .timePeriodMillis("{{firds.gleif.lei.throttle.time}}")
                .setHeader(Exchange.HTTP_QUERY, simple("filter[lei]=${body}"))
                .toD(https("{{firds.gleif.lei.url}}").throwExceptionOnFailure(true))
                .split()
                .jsonpath("{{firds.gleif.lei.tag}}")
                .process(exchange -> exchange.getIn()
                                             .setBody(createUpdateSQL(exchange.getIn())))
                .to(jdbc("default"))
                .to("log:GLEIF?level=INFO&groupInterval=10000&groupActiveOnly=true")
                .end();
        //end::GLEIF[]
    }

    //tag::createUpdateSQL[]

    /**
     * Create update sql string.
     *
     * @param in the in
     * @return the string
     */
    private String createUpdateSQL(final Message in) {

        final String body = in.getBody(String.class);
        final String issuer = in.getHeader("issuer", String.class);
        return String.format("UPDATE firds_data set jurisdiction='%s' where issuer='%s'",
                             StringUtils.trimAllWhitespace(body.replace("\"", "")),
                             issuer);
    }
    //end::createUpdateSQL[]


}
