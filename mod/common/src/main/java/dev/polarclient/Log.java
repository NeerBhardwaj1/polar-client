package dev.polarclient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Polar's log, through the log4j that every Minecraft version ships (messages are pre-formatted for old log4j). */
public final class Log {
	private static final Logger LOGGER = LogManager.getLogger("Polar Client");

	private Log() {
	}

	public static void info(String message) {
		LOGGER.info(message);
	}

	public static void warn(String message) {
		LOGGER.warn(message);
	}

	public static void warn(String message, Throwable t) {
		LOGGER.warn(message, t);
	}

	public static void error(String message, Throwable t) {
		LOGGER.error(message, t);
	}
}
