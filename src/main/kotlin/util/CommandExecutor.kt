package com.srw.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Result of a command execution
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val success: Boolean
)

/**
 * Executes a command line program and waits for its completion
 */
object CommandExecutor {

    /**
     * Executes a command and returns the result
     *
     * @param command The command to execute (e.g., "python", "/path/to/script.py")
     * @param args Arguments to pass to the command
     * @param workingDirectory Optional working directory for the command
     * @param timeoutMinutes Timeout in minutes (default: 10)
     * @return CommandResult containing exit code, output, and error
     */
    fun execute(
        command: String,
        args: List<String> = emptyList(),
        workingDirectory: String? = null,
        timeoutMinutes: Long = 10
    ): CommandResult {
        return try {
            val processBuilder = ProcessBuilder(listOf(command) + args)

            // Set working directory if provided
            if (workingDirectory != null) {
                processBuilder.directory(java.io.File(workingDirectory))
            }

            // Redirect error stream to output stream for easier reading
            processBuilder.redirectErrorStream(false)

            // Start the process
            val process = processBuilder.start()

            // Read output
            val output = StringBuilder()
            val error = StringBuilder()

            // Read stdout
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read output in separate threads to avoid deadlock
            val outputThread = Thread {
                outputReader.use { reader ->
                    reader.lineSequence().forEach { line ->
                        output.appendLine(line)
                    }
                }
            }

            val errorThread = Thread {
                errorReader.use { reader ->
                    reader.lineSequence().forEach { line ->
                        error.appendLine(line)
                    }
                }
            }

            outputThread.start()
            errorThread.start()

            // Wait for process to complete with timeout
            val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

            // Wait for reader threads to finish
            outputThread.join(1000)
            errorThread.join(1000)

            if (!completed) {
                process.destroyForcibly()
                return CommandResult(
                    exitCode = -1,
                    output = output.toString(),
                    error = "Command execution timed out after $timeoutMinutes minutes",
                    success = false
                )
            }

            val exitCode = process.exitValue()

            CommandResult(
                exitCode = exitCode,
                output = output.toString().trim(),
                error = error.toString().trim(),
                success = exitCode == 0
            )

        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                output = "",
                error = "Failed to execute command: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Executes a Python script
     *
     * @param scriptPath Path to the Python script
     * @param args Arguments to pass to the script
     * @param pythonCommand Python command (default: "python3")
     * @param workingDirectory Optional working directory
     * @param timeoutMinutes Timeout in minutes (default: 10)
     * @return CommandResult containing exit code, output, and error
     */
    fun executePython(
        scriptPath: String,
        args: List<String> = emptyList(),
        pythonCommand: String = "python3",
        workingDirectory: String? = null,
        timeoutMinutes: Long = 10
    ): CommandResult {
        return execute(
            command = pythonCommand,
            args = listOf(scriptPath) + args,
            workingDirectory = workingDirectory,
            timeoutMinutes = timeoutMinutes
        )
    }
}
