package se.hakuseki.route;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import se.hakuseki.beans.XmlToSqlBean;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.File;

/**
 * The type Delta import.
 */
public class DeltaImport extends EndpointRouteBuilder {
    /**
     * The Dbf.
     */
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    /**
     * The X path.
     */

    XPath xPath = XPathFactory.newInstance()
                              .newXPath();

    /**
     * Configure.
     */
    @Override
    public void configure() {
        final XPathBuilder xPathBuilder = new XPathBuilder("//NewRcrd | //TermntdRcrd");
        xPathBuilder.setThreadSafety(true);
        xPathBuilder.threadSafety(true);
        xPathBuilder.init(getContext());

        errorHandler(defaultErrorHandler());
        from(file("{{esma.delta.path}}").include(".*.zip")
                                        .delay(60_000L)
                                        .delete(true)
                                        .sortBy("${file:name}")
                                        .readLock("changed")
                                        .advanced()
                                        .synchronous(true))
                .routeId("Delta Import")
                .description("Imports ZIP files and persists in database")
                .autoStartup("{{esma.delta.startup}}")
                .idempotentConsumer(simple("${file:name}"),
                                    FileIdempotentRepository.fileIdempotentRepository(
                                            new File("FIRDS/data/FIRDSDelta.dat")))

                .to(direct("delta"))
                .choice()
                .when(simple("${header.CamelBatchComplete}"))
                .setBody(constant(""))
                .setHeader("JMSType", constant("ANNA")) //<.>
                .to(activemq("gleif.lei"))
                .log("Delta import completed!")
                .end();

        from(direct("delta"))
                .routeId("DELTA")
                .description("Unmarshal zip file, split on token, generate SQL statements and send to database.")
                .streamCaching()
                .log("Processing file ${file:name}, file #${header.CamelBatchIndex}++ out of ${header.CamelBatchSize} files")
                .unmarshal()
                .zipFile()
                .split()
                .tokenizeXML("FinInstrm")
                .streaming()
                .parallelProcessing(true)
                .choice()
                .when()
                .xpath(xPathBuilder.getText())
                .bean(XmlToSqlBean.class)
                .choice()
                .when(body().isNotNull())
                .to(jdbc("default"))
                .to(log("Delta Import").level(LoggingLevel.INFO.name())
                                       .groupActiveOnly(true)
                                       .groupInterval(60_000L))
                .end()
                .choice()
                .when(simple("${header.CamelSplitComplete} == true"))
                .log("Number of records split: ${header.CamelSplitSize}")
                .log("Importing complete: ${header.CamelFileName}")
                .end();
    }
}
//end::DeltaImport[]