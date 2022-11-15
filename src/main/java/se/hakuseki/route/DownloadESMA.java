package se.hakuseki.route;

import org.apache.activemq.ScheduledMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.jdbc.JdbcOutputType;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.util.StringUtils;
import se.hakuseki.beans.HttpDownload;

import javax.inject.Inject;
import java.io.File;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DownloadESMA extends EndpointRouteBuilder {
    public static final String GLEIF = "GLEIF";
    public static final String DEFAULT = "default";

    @Override
    public void configure() {

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
                .to(jdbc(DEFAULT).outputType(JdbcOutputType.SelectList))
                .split(body())
                .streaming()
                .parallelProcessing(true)
                .process(exchange -> {
                    final Message in = exchange.getIn();
                    final Map<String, String> body = in.getBody(Map.class);
                    final String issuer = body.get("issuer");
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
                .to(jdbc(DEFAULT))
                .to("log:GLEIF?level=INFO&groupInterval=10000&groupActiveOnly=true")
                .end();
        //end::GLEIF[]


        //tag::ANNA[]
        from(activemq("gleif.lei").selector("JMSType='ANNA'"))
                .routeId("DOWNLOAD-ANNA").description("Downloads the GLEIF-ANNA ISIN to LEI files for imports")
                .autoStartup("{{firds.gleif.anna.startup}}")
                .to(direct("ANNA"))
                .setBody(constant(""))
                .setHeader("JMSType", constant("LEI"))
                .to(activemq("gleif.lei"))
                .log("ANNA download completed!")
                .end();

        from(direct("ANNA"))
                .routeId("ANNA")
                .streamCaching()
                .setHeader("CamelHttpMethod", constant("GET"))
                .to(https("{{firds.gleif.anna.url}}").throwExceptionOnFailure(true))
                .split()
                .jsonpathWriteAsString("{{firds.gleif.anna.tag}}")
                .process(exchange -> {
                    //Remove starting/ending quotation marks
                    final Message in = exchange.getIn();
                    final String body = in.getBody(String.class);
                    in.setBody(body.replace("\"", ""));
                })
                .choice()
                .when(body().isNotNull())
                .idempotentConsumer(body(),
                        FileIdempotentRepository.fileIdempotentRepository(new File("FIRDS/data/anna.dat")))
                .setHeader("CamelHttpMethod", constant("GET"))
                .log("Streaming ANNA Download: ${body}")
                .toD("${body}")
                .unmarshal()
                .zipFile()
                .split(bodyAs(String.class).tokenize("\n"))
                .streaming()
                .parallelProcessing(true)
                .unmarshal()
                .csv()
                .process(exchange -> {
                    final Message in = exchange.getIn();
                    //noinspection unchecked
                    final List<List<String>> body = in.getBody(List.class);
                    final String sql = createValidSQL(body);
                    in.setBody(sql);
                })
                .choice()
                .when(body().isNotNull())
                .to(jdbc(DEFAULT))
                .to("log:ANNAfile?level=INFO&groupInterval=60000&groupActiveOnly=true")
                .end()
                .choice()
                .when(simple("${header.CamelSplitComplete} == true"))
                .log("Number of records split: ${header.CamelSplitSize}")
                .log("Importing complete: ${header.CamelFileName}")
                .end();
        //end::ANNA[]

        //tag::FULL[]
        from(quartz("FIRDSDownloader/Full").cron("{{esma.download.timer.full}}")
                .recoverableJob(true))
//                                    from(timer("FIRDSDownloader/Full").repeatCount(1))
                .routeId("FIRDS Download Full")
                .description("Downloading FUL files from ESMA")
                .autoStartup("{{esma.download.startup}}")
                .setHeader("fileType", constant("FULL"))
                .to(direct("DOWNLOAD-FIRDS"))
                .end();
        //end::FULL[]

        //tag::DELTA[]
//        from(quartz("FIRDSDownloader/Delta").cron("{{esma.download.timer.delta}}")
                                                                                from(timer("FIRDSDownloader/Delta").repeatCount(1))

//                                            .recoverableJob(true))
                .routeId("FIRDS Download Delta")
                .description("Downloading DLT_ files from ESMA")
                .autoStartup("{{esma.download.startup}}")
                .setHeader("fileType", constant("DELTA"))
                .to(direct("DOWNLOAD-FIRDS"))
                .end();
        //end::DELTA[]

        //tag::FIRDS[]
        from(direct("DOWNLOAD-FIRDS"))
                .routeId("DOWNLOAD-FIRDS-ROUTE")
                .description("Common route for downloading FUL and DLT files from ESMA")
                .streamCaching()
                .setHeader("CamelHttpMethod", constant("GET"))
                .log("{{esma.download.url}}")
                .setHeader(Exchange.HTTP_QUERY, //<.>
                        simple(
                                "q=*&fq=publication_date:[${date:now-24h:yyyy-MM-dd}T00:00:00Z+TO+${date:now-24h:yyyy-MM-dd}T23:59:59Z]&wt=xml&indent=false&start=0&rows=100"))
                .toD(https("{{esma.download.url}}"))
                .log("${body}")
                .split()
                .tokenizeXML("doc", "result")
                .streaming()
                .parallelProcessing()
                .bean(HttpDownload.class)
                .choice()
                .when(body().isNotNull())
                .setHeader("CamelHttpMethod", constant("GET"))
                .process(exchange -> {
                    final String body = exchange.getIn()
                            .getBody(String.class);
                    if (null == body) return;
                    final URL url = new URL(body);
                    exchange.getIn()
                            .setHeader("CamelOverruleFileName", FilenameUtils.getName(url.getPath()));
                })
                .log("${body}")
                .toD("${body}")
                .log("Saving FIRDS Download: ${header.CamelOverruleFileName}")
                .choice()
                .when(header("CamelOverruleFileName").startsWith("FULINS_"))
                .to(file("{{esma.full.path}}"))
                .when(header("CamelOverruleFileName").startsWith("DLTINS_"))
                .to(file("{{esma.delta.path}}"))
                .end()
                .log("FIRDS download complete")
                .end();
    }
    //end::FIRDS[]

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


    //tag::createValidSQL[]

    /**
     * Create valid sql string.<br>
     * <p>
     * Checks for valid structure of a LEI and ISIN
     * </p>
     * <ul>
     *     <li>ISIN = 12 characters (https://www.isindb.com/validate-isin/(</li>
     *     <li>LEI = 20 characters (https://www.gleif.org/en/about-lei/iso-17442-the-lei-code-structure)</li>
     * </ul>
     *
     * @param body the body
     * @return the string
     */
    private String createValidSQL(final List<List<String>> body) {
        final String lei = body.get(0)
                .get(0);
        final String isin = body.get(0)
                .get(1);

        String sql = null;
        if (Pattern.matches("([A-Z]{2})([A-Z0-9]{9})(\\d)", isin) &&
                Pattern.matches("([A-Z0-9]{4})([A-Z0-9]{14})(\\d{2})", lei)) {
            sql = MessageFormat.format("update firds_data set issuer=''{1}'' where isin=''{0}'' AND issuer <> ''{1}''",
                    isin,
                    lei);
        }
        log.trace(String.format("sql = %s", sql));
        return sql;
    }
    //end::createValidSQL[]
}
