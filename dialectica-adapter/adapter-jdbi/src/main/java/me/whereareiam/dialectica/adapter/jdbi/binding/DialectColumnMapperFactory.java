package me.whereareiam.dialectica.adapter.jdbi.binding;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaConfig;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.mapper.ColumnMapperFactory;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Reads {@link java.util.UUID}, {@link Boolean}, {@link java.time.Instant} and
 * {@link java.time.LocalDate} columns through the dialect that
 * {@link DialecticaPlugin} chose; the plugin registers it. A
 * primitive {@code boolean} reads {@code NULL} as {@code false}, as Jdbi does for primitives.
 */
public final class DialectColumnMapperFactory implements ColumnMapperFactory {
	/**
	 * Creates the factory.
	 */
	public DialectColumnMapperFactory() {
	}

	@Override
	public Optional<ColumnMapper<?>> build(Type type, ConfigRegistry config) {
		ColumnType columnType = DialectArgumentFactory.columnType(type);
		if (columnType == null) return Optional.empty();

		Dialect dialect = config.get(DialecticaConfig.class).dialect();
		if (type == boolean.class)
			return Optional.of((row, column, context) -> Boolean.TRUE.equals(dialect.read(row, column, columnType)));

		return Optional.of((row, column, context) -> dialect.read(row, column, columnType));
	}
}
