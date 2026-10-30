package io.datadynamics.atlas.client.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/** Base of the leaf commands: output, input and the root command's environment. */
abstract class CliCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    protected AtlasCli root() {
        return (AtlasCli) spec.root().userObject();
    }

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    /** Asks on standard input; only {@code yes} confirms. */
    protected boolean confirm(String question) {
        PrintWriter out = out();
        out.print(question + " Only 'yes' will be accepted: ");
        out.flush();
        String answer = root().readLine();
        out.println();
        return answer != null && answer.trim().equals("yes");
    }
}
