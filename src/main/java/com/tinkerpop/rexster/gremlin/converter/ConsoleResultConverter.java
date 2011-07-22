package com.tinkerpop.rexster.gremlin.converter;

import java.io.Writer;
import java.lang.reflect.Array;
import java.util.*;

public class ConsoleResultConverter implements ResultConverter<List<String>> {

    private final Writer outputWriter;

    public ConsoleResultConverter(final Writer outputWriter) {
        this.outputWriter = outputWriter;
    }

    public List<String> convert(final Object result) throws Exception {
        try {
            List<Object> resultLines = new ArrayList<Object>();
            if (result == null) {
              resultLines = new ArrayList<Object>();
            } else if (result instanceof Iterable) {
                for (Object o : (Iterable) result) {
                    resultLines.add(o);
                }
            } else if (result instanceof Iterator) {
                // Table is handled through here and the toString() to get it formatted.
                Iterator itty = (Iterator) result;
                while (itty.hasNext()) {
                    resultLines.add(itty.next());
                }
            } else if (result.getClass().isArray()) {
                int length = Array.getLength(result);
                for (int ix = 0; ix < length; ix++) {
                    resultLines.add(Array.get(result, ix).toString());
                }
            } else if (result instanceof Map) {
                Map map = (Map) result;
                for (Object key : map.keySet()) {
                    resultLines.add(key + "=" + map.get(key).toString());
                }
            } else if (result instanceof Throwable) {
                resultLines.add(((Throwable) result).getMessage());
            } else {
                resultLines.add(result);
            }

            // Handle output data
            List<String> outputLines = new ArrayList<String>();

            // Handle eval() result
            String[] printLines = this.outputWriter.toString().split("\n");

            if (printLines.length > 0 && printLines[0].length() > 0) {
                for (String printLine : printLines) {
                    outputLines.add(printLine);
                }
            }

            if (resultLines == null
                    || resultLines.size() == 0
                    || (resultLines.size() == 1 && (resultLines.get(0) == null || resultLines
                    .get(0).toString().length() == 0))) {
                // Result was empty, add empty text if there was also no IO
                // output
                if (outputLines.size() == 0) {
                    outputLines.add("");
                }
            } else {
                // Make sure all lines are strings
                for (Object resultLine : resultLines) {
                    outputLines.add(resultLine != null ? resultLine.toString() : "null");
                }
            }

            return outputLines;
        } catch (Exception ex) {
            ArrayList<String> resultList = new ArrayList<String>();
            resultList.add(ex.getMessage());
            return resultList;
        }
    }
}
