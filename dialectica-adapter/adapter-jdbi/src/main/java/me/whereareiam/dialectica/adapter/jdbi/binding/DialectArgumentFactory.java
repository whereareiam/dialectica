package me.whereareiam.dialectica.adapter.jdbi.binding;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaConfig;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Binds {@link java.util.UUID}, {@link Boolean}, {@link Instant} and {@link LocalDate} parameters through the dialect that
 * {@link DialecticaPlugin} chose; the plugin registers it.
 */
public final class DialectArgumentFactory implements ArgumentFactory.Preparable {
	/**
	 * Creates the factory.
	 */
	public DialectArgumentFactory() {
	}

	@Override
	public Optional<Function<Object, Argument>> prepare(Type type, ConfigRegistry config) {
		ColumnType columnType = columnType(type);
		if (columnType == null) return Optional.empty();

		Dialect dialect = config.get(DialecticaConfig.class).dialect();

		return Optional.of(value -> (position, statement, context) -> dialect.bind(statement, position, columnType, value));
	}

	static ColumnType columnType(Type type) {
		if (type == UUID.class) return ColumnType.uuid();
		if (type == Boolean.class || type == boolean.class) return ColumnType.bool();
		if (type == Instant.class) return ColumnType.instant();
		if (type == LocalDate.class) return ColumnType.date();

		return null;
	}
}
