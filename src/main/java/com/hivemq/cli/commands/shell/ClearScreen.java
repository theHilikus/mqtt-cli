package com.hivemq.cli.commands.shell;

import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;


/**
 * Command that clears the screen.
 */
@CommandLine.Command(
        name = "cls", aliases = "clear", mixinStandardHelpOptions = true,
        description = "Clears the screen", version = "1.0")
class ClearScreen implements Callable<Void> {

    @CommandLine.ParentCommand
    Shell parent;

    public Void call() throws IOException {
        parent.reader.clearScreen();
        return null;
    }
}