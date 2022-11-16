package se.hakuseki.beans;

import org.apache.camel.language.xpath.XPath;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Currency;

/**
 * The type Xml to sql bean.
 */
public class XmlToSqlBean {
    /**
     * The constant LOG.
     */
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(XmlToSqlBean.class);

    /**
     * To sql string.
     *
     * @param isin            the isin
     * @param currency        the currency
     * @param fullName        the full name
     * @param venue           the venue
     * @param classification  the classification
     * @param terminationDate the termination date
     * @param issuer          the issuer
     * @param maturityDate    the maturity date
     * @param termnRecord     the termn record
     * @param newRecord       the new record
     * @return the string
     */
    public String toSql(@XPath("//FinInstrmGnlAttrbts/Id") final String isin,
                        @XPath("//NtnlCcy") final String currency,
                        @XPath("//FullNm") final String fullName,
                        @XPath("//TradgVnRltdAttrbts/Id") final String venue,
                        @XPath("//ClssfctnTp") final String classification,
                        @XPath("//TradgVnRltdAttrbts/TermntnDt") final String terminationDate,
                        @XPath("//Issr") final String issuer,
                        @XPath("//MtrtyDt") String maturityDate,
                        @XPath("//TermntdRcrd") final String termnRecord,
                        @XPath("//NewRcrd") final String newRecord) {
        String         sqlStr        = null;
        final String[] fullNameArray = fullName.split(" ");
        String         priceCurrency = "XX";


        for (final String tmpCurr : fullNameArray) {
            try {
                final Currency tmpCurrency = Currency.getInstance(tmpCurr);
                if (!tmpCurrency.getCurrencyCode()
                                .equalsIgnoreCase(currency)) {
                    priceCurrency = tmpCurrency.getCurrencyCode();
                    break;
                }
            } catch (final Exception ignored) {
            }
        }
        final String fullNameFixed = StringUtils.replace(fullName, "'", "''");
        if (StringUtils.isEmpty(maturityDate)) {
            maturityDate = LocalDate.of(9999, 12, 31)
                                    .toString();
        }


        LocalDateTime termDate = LocalDateTime.of(2199, 12, 31, 23, 59, 59, 0);
        try {
            termDate = LocalDateTime.parse(terminationDate);
        } catch (final DateTimeParseException ignored) {
        }

        if (StringUtils.isEmpty(termnRecord)) {
            sqlStr = MessageFormat.format(
                    "insert into firds_data (isin, fullname, currency, maturity_date, venue, classification,price_currency, termination_date, issuer) " +
                    "values(''{0}'', ''{3}'', ''{1}'', ''{2}'', ''{4}'', ''{5}'', ''{6}'', ''{7}'', ''{8}'') ON CONFLICT DO NOTHING",
                    isin, currency, maturityDate, StringUtils.trim(fullNameFixed), venue, classification, priceCurrency,
                    termDate, issuer);
            LOG.trace(
                    "isin = {}, currency = {}, fullName = {}, venue = {}, classification = {}, terminationDate = {}, issuer = {}",
                    isin, currency, fullName, venue, classification, termDate, issuer);
        } else {
            sqlStr = MessageFormat.format(
                    "DELETE from firds_data where isin=''{0}'' AND currency=''{1}'' AND venue=''{2}''", isin, currency,
                    venue);
            LOG.trace("Deleting record ISIN {}, Currency {}, Venue {}", isin, currency, venue);
        }
        return sqlStr;
    }
}
