package com.tinkerpop.rexster.protocol;

import com.tinkerpop.pipes.SingleIterator;
import com.tinkerpop.rexster.Tokens;
import com.tinkerpop.rexster.protocol.message.ConsoleScriptResponseMessage;
import com.tinkerpop.rexster.protocol.message.RexProMessage;
import com.tinkerpop.rexster.protocol.message.ScriptRequestMessage;
import jline.ConsoleReader;
import jline.History;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.SimpleBindings;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class RexsterConsole {

    private RemoteRexsterSession session = null;
    private String host;
    private String language;
    private int port;

    private final PrintStream output = System.out;

    private static final String REXSTER_HISTORY = ".rexster_history";

    public RexsterConsole(String host, int port, String language) throws Exception {

        this.output.println("        (l_(l");
        this.output.println("(_______( 0 0");
        this.output.println("(        (-Y-)");
        this.output.println("l l-----l l");
        this.output.println("l l,,   l l,,");

        this.host = host;
        this.port = port;
        this.language = language;

        this.session = new RemoteRexsterSession(this.host, this.port);

        this.primaryLoop();
    }

    public void primaryLoop() throws Exception {

        final ConsoleReader reader = new ConsoleReader();
        reader.setBellEnabled(false);
        reader.setUseHistory(true);

        try {
            History history = new History();
            history.setHistoryFile(new File(REXSTER_HISTORY));
            reader.setHistory(history);
        } catch (IOException e) {
            System.err.println("Could not find history file");
        }

        String line = "";
        this.output.println();

        while (line != null) {

            try {
                line = "";
                boolean submit = false;
                boolean newline = false;
                while (!submit) {
                    if (newline)
                        line = line + "\n" + reader.readLine(RexsterConsole.makeSpace(this.getPrompt().length()));
                    else
                        line = line + "\n" + reader.readLine(this.getPrompt());
                    if (line.endsWith(" .")) {
                        newline = true;
                        line = line.substring(0, line.length() - 2);
                    } else {
                        line = line.trim();
                        submit = true;
                    }
                }

                if (line.isEmpty())
                    continue;
                if (line.equals(Tokens.REXSTER_CONSOLE_QUIT)) {
                    if (this.session != null) {
                        this.session.close();
                        this.session = null;
                    }
                    return;
                } else if (line.equals(Tokens.REXSTER_CONSOLE_HELP))
                    this.printHelp();
                else if (line.equals(Tokens.REXSTER_CONSOLE_BINDINGS)) {
                    //this.printBindings(this.rexster.getBindings(ScriptContext.ENGINE_SCOPE));
                } else if (line.startsWith(Tokens.REXSTER_CONSOLE_LANGUAGE)) {
                    this.language = line.substring(1);
                } else {
                    Object result = eval(line, this.language, this.session);
                    Iterator itty;
                    if (result instanceof Iterator) {
                        itty = (Iterator) result;
                    } else if (result instanceof Iterable) {
                        itty = ((Iterable) result).iterator();
                    } else if (result instanceof Map) {
                        itty = ((Map) result).entrySet().iterator();
                    } else {
                        itty = new SingleIterator<Object>(result);
                    }

                    while (itty.hasNext()) {
                        this.output.println("==>" + itty.next());
                    }
                }

            } catch (Exception e) {
                this.output.println("Evaluation error: " + e.getMessage());
            }
        }
    }

    public void printHelp() {
        this.output.println("-= Console Specific =-");
        this.output.println("?<lang-name>: jump to engine");
        this.output.println(Tokens.REXSTER_CONSOLE_QUIT + ": quit");
    }

    public void printBindings(final Bindings bindings) {
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            this.output.println(entry);
        }
    }

    public String getPrompt() {
        return "rexster[" + this.language +  "]> ";
    }

    public static String makeSpace(int number) {
        String space = new String();
        for (int i = 0; i < number; i++) {
            space = space + " ";
        }
        return space;
    }

    private static Object eval(String script, String scriptEngineName, RemoteRexsterSession session) {

        Object returnValue = null;

        try {
            session.open();

            // pass in some dummy rexster bindings...not really fully working quite right for scriptengine usage
            final RexProMessage scriptMessage = new ScriptRequestMessage(
                    session.getSessionKey(), scriptEngineName, new RexsterBindings(), script);

            final RexProMessage resultMessage = RexPro.sendMessage(
                    session.getRexProHost(), session.getRexProPort(), scriptMessage);

            final ConsoleScriptResponseMessage responseMessage = new ConsoleScriptResponseMessage(resultMessage);

            /*
            RexsterBindings bindingsFromServer = responseMessage.getBindings();

            // apply bindings from server to local bindings so that they are in synch
            if (bindingsFromServer != null) {
                bindings.putAll(bindingsFromServer);
            }
            */

            ArrayList<String> lines = new ArrayList<String>();
            ByteBuffer bb = ByteBuffer.wrap(responseMessage.getBody());

            // navigate to the start of the results
            int lengthOfBindings = bb.getInt();
            bb.position(lengthOfBindings + 4);

            while (bb.hasRemaining()) {
                int segmentLength = bb.getInt();
                byte[] resultObjectBytes = new byte[segmentLength];
                bb.get(resultObjectBytes);

                lines.add(new String(resultObjectBytes));
            }

            returnValue = lines.iterator();

            if (lines.size() == 1) {
                returnValue = lines.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }

        return returnValue;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.out.println("Rexster Console expects three parameters in the following order: host port language");
        } else {

            String host = args[0];

            int port = 0;
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                System.out.println("The port parameter must be an integer value.");
            }

            String language = args[2];

            new RexsterConsole(host, port, language);
        }
    }
}
