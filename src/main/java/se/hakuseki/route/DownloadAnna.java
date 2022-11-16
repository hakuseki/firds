package se.hakuseki.route;

import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;

import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;

public class DownloadAnna extends EndpointRouteBuilder {
    @Override
    public void configure() throws Exception {

        //tag::ANNA[]
        from(activemq("gleif.lei").selector("JMSType='ANNA'"))
                .routeId("DOWNLOAD-ANNA")
                .description("Downloads the GLEIF-ANNA ISIN to LEI files for imports")
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
                    final Message in   = exchange.getIn();
                    final String  body = in.getBody(String.class);
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
                    final String             sql  = createValidSQL(body);
                    in.setBody(sql);
                })
                .choice()
                .when(body().isNotNull())
                .to(jdbc("default"))
                .to("log:ANNAfile?level=INFO&groupInterval=60000&groupActiveOnly=true")
                .end()
                .choice()
                .when(simple("${header.CamelSplitComplete} == true"))
                .log("Number of records split: ${header.CamelSplitSize}")
                .log("Importing complete: ${header.CamelFileName}")
                .end();
        //end::ANNA[]
    }

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
