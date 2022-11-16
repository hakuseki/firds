package se.hakuseki.beans;

import org.apache.camel.Header;
import org.apache.camel.language.xpath.XPath;

import java.util.regex.Pattern;

/**
 * The type Http download.
 *
 * Predicate for determining the file names of FUL and DLT files
 */
public class HttpDownload {
    /**
     * To link string.
     *
     * @param fileName the file name
     * @param link     the link
     * @param fileType the file type
     * @return the string
     */
    public String toLink(@XPath("/doc/str[@name='file_name']") final String fileName,
                         @XPath("/doc/str[@name='download_link']") final String link,
                         @Header("fileType") final String fileType) {
        final String result;

        final Pattern regexFull  = Pattern.compile("FULINS_", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        final Pattern regexDelta = Pattern.compile("DLTINS_", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        switch (fileType) {
            case "FULL":
                result = regexFull.matcher(fileName)
                                  .find() ? link : null;
                break;
            case "DELTA":
                result = regexDelta.matcher(fileName)
                                   .find() ? link : null;
                break;
            default:
                result = null;
                break;
        }

        return result;
    }
}
