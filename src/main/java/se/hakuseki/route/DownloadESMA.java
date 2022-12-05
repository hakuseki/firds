package se.hakuseki.route;

import org.apache.camel.Exchange;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.commons.io.FilenameUtils;
import se.hakuseki.beans.HttpDownload;

import java.net.URL;

/**
 * The type Download esma.
 * Downloads FUL and DLT files from ESMA service and stores them on disk
 */
public class DownloadESMA extends EndpointRouteBuilder {

    /**
     * Configure.
     */
    @Override
    public void configure() {
        //tag::FULL[]
        from(quartz("FIRDSDownloader/Full").cron("{{esma.download.timer.full}}")
                                           .recoverableJob(true))
                .routeId("FIRDS Download Full")
                .description("Downloading FUL files from ESMA")
                .autoStartup("{{esma.download.startup}}")
                .setHeader("fileType", constant("FULL"))
                .to(direct("DOWNLOAD-FIRDS"))
                .end();
        //end::FULL[]

        //tag::DELTA[]
        from(quartz("FIRDSDownloader/Delta").cron("{{esma.download.timer.delta}}")
                                            .recoverableJob(true))
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
                .setHeader(Exchange.HTTP_QUERY,
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


}
