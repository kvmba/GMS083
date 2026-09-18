package org.gms.provider;

import org.gms.provider.wz.XMLWZData;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the WZ numeric parser against the Locale regression that broke every snow map.
 *
 * <p>The v83 tree mixes two fraction conventions: every {@code <float>} writes a dot
 * ({@code info/fs="0.2"}, {@code mobRate="1.5"}) while a handful of {@code <double>}
 * ({@code unitPrice}, {@code recovery}) write a comma ({@code "0,3"}). The old parser used one
 * global {@link java.text.NumberFormat} whose Locale was chosen by the
 * {@code use_unit_price_with_comma} config ("true" &rarr; {@link java.util.Locale#FRANCE}). With
 * FRANCE, {@code "0.2"} parses to {@code 0}, so {@code MapleMap.getFootholdSpeed()} returned 0 and
 * {@code MapleMovement.slipScale} treated El Nath as firm ground &mdash; bots (and players) stopped
 * sliding on snow, snowshoes or not. {@link DataTool#parseNumber(String)} now picks the separator by
 * content, so both conventions parse correctly with no Locale dependence.
 */
class DataToolParseNumberTest {

    @Test
    void dotFractionParsesRegardlessOfLocale() {
        // fs="0.2" is the exact literal on the 73 El Nath snow maps; slipScale treats fs in (0,1)
        // as slippery. Anything but 0.2 here silently disables snow sliding.
        assertEquals(0.2d, DataTool.parseNumber("0.2").floatValue(), 1e-6);
        assertEquals(0.5d, DataTool.parseNumber("0.5").floatValue(), 1e-6);
        assertEquals(0.9d, DataTool.parseNumber("0.9").floatValue(), 1e-6);
        assertEquals(1.5d, DataTool.parseNumber("1.5").floatValue(), 1e-6);
        assertEquals(2.999999d, DataTool.parseNumber("2.999999").floatValue(), 1e-6);
        assertEquals(10.0d, DataTool.parseNumber("10.0").floatValue(), 1e-6);
    }

    @Test
    void commaFractionParsesAsDecimal() {
        // unitPrice / recovery in the tree use a comma as the fraction separator.
        assertEquals(0.3d, DataTool.parseNumber("0,3").doubleValue(), 1e-9);
        assertEquals(1.5d, DataTool.parseNumber("1,5").doubleValue(), 1e-9);
        assertEquals(0.05d, DataTool.parseNumber("0,05").doubleValue(), 1e-9);
    }

    @Test
    void integersAndSignsAreUnchanged() {
        assertEquals(0, DataTool.parseNumber("0").intValue());
        assertEquals(10, DataTool.parseNumber("10").intValue());
        assertEquals(30, DataTool.parseNumber("30").intValue());
        assertEquals(-7, DataTool.parseNumber("-7").intValue());
        assertEquals(1, DataTool.parseNumber("1.0").intValue());
        assertEquals(2, DataTool.parseNumber("2.999999").intValue()); // truncation, as before
    }

    @Test
    void nullAndUnparseableFallBackToZero() {
        assertEquals(0, DataTool.parseNumber(null).intValue());
        assertEquals(0, DataTool.parseNumber("").intValue());
        assertEquals(0, DataTool.parseNumber("abc").intValue());
        assertEquals(0, DataTool.parseNumber("e1").intValue());
    }

    /**
     * End-to-end through the real {@link XMLWZData} parse path: the tag type is what selects the
     * {@code doubleValue()/floatValue()} boxing, so this pins the whole chain (XML &rarr; getData)
     * rather than just the helper.
     */
    @Test
    void xmlwzDataParsesFloatAndDoubleLiterals() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<imgdir name=\"t.img\"><imgdir name=\"info\">"
                + "<float name=\"fs\" value=\"0.2\"/>"
                + "<float name=\"mobRate\" value=\"1.5\"/>"
                + "<int name=\"reqLevel\" value=\"30\"/>"
                + "<double name=\"unitPrice\" value=\"0,3\"/>"
                + "</imgdir></imgdir>";
        Path tmp = Files.createTempFile("wznum", ".xml");
        try {
            Files.write(tmp, xml.getBytes(StandardCharsets.UTF_8));
            XMLWZData root = parse(tmp);

            assertEquals(0.2f, (Float) root.getChildByPath("info/fs").getData(), 1e-6f);
            assertEquals(1.5f, (Float) root.getChildByPath("info/mobRate").getData(), 1e-6f);
            assertEquals(30, (Integer) root.getChildByPath("info/reqLevel").getData());
            assertEquals(0.3d, (Double) root.getChildByPath("info/unitPrice").getData(), 1e-9);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static XMLWZData parse(Path xml) throws IOException {
        try (FileInputStream fis = new FileInputStream(xml.toFile())) {
            return XMLWZData.parse(fis);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
