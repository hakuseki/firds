package se.hakuseki.route;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import se.hakuseki.beans.XmlToSqlBean;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;

public class FullImport extends EndpointRouteBuilder {
    /**
     * Configure.
     */
    @Override
    public void configure() throws ParserConfigurationException {

        errorHandler(defaultErrorHandler());

        from(file("{{esma.full.path}}").include(".*.zip")
                                       .delete(true)
                                       .delay(60_000L)
                                       .sortBy("${file:name}")
                                       .readLock("changed")
                                       .advanced()
                                       .synchronous(true))
                .routeId("Full Import")
                .description("Imports FUL files and persists in database")
                .autoStartup("{{esma.full.startup}}")
                .idempotentConsumer(simple("${file:name}"),
                                    FileIdempotentRepository.fileIdempotentRepository(
                                            new File("FIRDS/data/FIRDSFull.dat")))
                .to(direct("Full"))
                .log("Full import completed!")
                .end();

        from(direct("Full"))
                .routeId("FULL")
                .streamCaching()
                .log("Processing file ${file:name}, file #${header.CamelBatchIndex}++ out of ${header.CamelBatchSize} files")
                .unmarshal()
                .zipFile()
                .split()
                .tokenizeXML("RefData")
                .streaming()
                .parallelProcessing(true)
                .bean(XmlToSqlBean.class)
                .choice()
                .when(body().isNotNull())
                .to(jdbc("default"))
                .to(log("Full Import").level(LoggingLevel.INFO.toString())
                                      .groupInterval(60_000L)
                                      .groupActiveOnly(true))
                .end()
                .choice()
                .when(simple("${header.CamelSplitComplete} == true"))
                .log("Number of records split: ${header.CamelSplitSize}")
                .log("Importing complete: ${header.CamelFileName}")
                .end();
    }
}
