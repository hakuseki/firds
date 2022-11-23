package se.hakuseki.route;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.jdbc.JdbcOutputType;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;


/**
 * The type Rest service.
 */
public class RestService extends EndpointRouteBuilder {
    /**
     * Configure.
     */
    @Override
    public void configure() {
        restConfiguration()
                //.component("servlet")
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("prettyPrint", "true")
                .apiContextPath("/api")
                .apiProperty("api.title", "FIRDS API")
                .apiProperty("api.description", "API for querying instruments from FIRDS")
                .apiProperty("api.version", "1.0.0")
                .apiProperty("cors", "true");

        rest("/firds")
                .produces("application/json")
                .description("REST-FIRDS", "This is the FIRDS database", "en")
                .get()
                .description("Fetches all instruments")
                .responseMessage()
                .code(200)
                .message("All instruments successfully returned")
                .endResponseMessage()
                .to("direct:getAllFIRDS")

                .get("/{isin}/{currency}/{venue}")
                .description("Get Instrument based on ISIN, Currency and Venue")
                .param()
                .name("isin")
                .type(RestParamType.header)
                .description("The ISIN of the instruments")
                .dataType("string")
                .endParam()
                .param()
                .name("currency")
                .type(RestParamType.header)
                .description("Currency")
                .dataType("string")
                .endParam()
                .param()
                .name("venue")
                .type(RestParamType.header)
                .description("Venue")
                .dataType("string")
                .endParam()
                .responseMessage()
                .code(200)
                .message("Instrument(s) successfully returned")
                .endResponseMessage()
                .to("direct:getIsinCurrVenueFIRDS")

                .get("/{isin}/{currency}")
                .description("Get Instrument based on ISIN and Currency")
                .param()
                .name("isin")
                .type(RestParamType.header)
                .description("The ISIN of the instruments")
                .dataType("string")
                .endParam()
                .param()
                .name("currency")
                .type(RestParamType.header)
                .description("Currency")
                .dataType("string")
                .endParam()
                .responseMessage()
                .code(200)
                .message("Instrument(s) successfully returned")
                .endResponseMessage()
                .to("direct:getIsinCurrFIRDS")

                .get("/{isin}")
                .description("GET-ISIN-FIRDS", "Find instruments by ISIN", "en")
                .param()
                .name("isin")
                .type(RestParamType.header)
                .description("The ISIN of the instruments")
                .dataType("string")
                .endParam()
                .responseMessage()
                .code(200)
                .message("Instrument(s) successfully returned")
                .endResponseMessage()
                .to("direct:getIsinFIRDS")

                .get("/{currency}/{priceCurrency}/{maturityDate}/{cfi}")
                .description("REST-QUERY-FX",
                             "Fetch instrument from Currency, Price Currency, Maturity date and Classification",
                             "en")
                .param()
                .name("currency")
                .type(RestParamType.header)
                .description("Notional currency")
                .dataType("string")
                .endParam()
                .param()
                .name("priceCurrency")
                .type(RestParamType.header)
                .description("Price/Quote Currency")
                .dataType("string")
                .endParam()
                .param()
                .name("maturityDate")
                .type(RestParamType.header)
                .description("Maturity date as yyyy-MM-dd")
                .dataType("string")
                .endParam()
                .param()
                .name("cfi")
                .type(RestParamType.header)
                .description("Instrument classification. Starts with…")
                .dataType("string")
                .endParam()
                .responseMessage()
                .code(200)
                .message("Instrument successfully returned")
                .endResponseMessage()
                .to("direct:getInstrumentFIRDS");


        from(direct("getAllFIRDS"))
                .routeId("GET-ALL-FIRDS")
                .description("Fetch 100 records from database")
                .setBody(simple("select * from firds_data limit 100"))
                .to(jdbc("default").outputType(JdbcOutputType.SelectList));

        from(direct("getIsinFIRDS"))
                .routeId("GET-ISIN-FIRDS")
                .description("Fetch record based on ISIN")
                .setBody(simple("select * from firds_data where isin = '${header.isin.toUpperCase()}'"))
                .to(jdbc("default").outputType(JdbcOutputType.SelectList));

        from(direct("getInstrumentFIRDS"))
                .routeId("GET-INSTRUMENT-FIRDS")
                .description("Fetch records on currency, price currency and maturity date")
                .setBody(simple(
                        "select * from firds_data where currency='${header.currency.toUpperCase()}' AND " +
                        "price_currency='${header.priceCurrency.toUpperCase()}' AND " +
                        "maturity_Date='${header.maturityDate}' AND classification like '${header.cfi.toUpperCase()}%'"))
                .to(jdbc("default"));

        from(direct("getIsinCurrVenueFIRDS"))
                .routeId("GET-INSTRUMENT-FIRDS-2")
                .description("Fetch records on isin, currency and venue")
                .setBody(simple(
                        "select * from firds_data where isin='${header.isin.toUpperCase()}' AND currency='${header.currency.toUpperCase()}' AND " +
                        "venue='${header.venue.toUpperCase()}'"))
                .to(jdbc("default").outputType(JdbcOutputType.SelectOne));

        from(direct("getIsinCurrFIRDS"))
                .routeId("GET-INSTRUMENT-FIRDS-3")
                .description("Fetch records on isin, currency")
                .setBody(simple(
                        "select * from firds_data where isin='${header.isin.toUpperCase()}' AND currency='${header.currency.toUpperCase()}'"))
                .to(jdbc("default").outputType(JdbcOutputType.SelectList));
    }
}
