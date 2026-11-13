package me.whereareiam.dialectica.common.entity;

import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.entity.annotation.Column;
import me.whereareiam.dialectica.entity.annotation.Default;
import me.whereareiam.dialectica.entity.annotation.ForeignKey;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Index;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.References;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.dialectica.entity.annotation.Unique;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.ForeignKeyDefinition;
import me.whereareiam.dialectica.schema.IndexDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads the {@link EntityTable} of an annotated record or class through reflection.
 * <p>
 * Every non-static, non-transient field declared by the type itself is a column, in declaration
 * order. Values are read through the record accessor, a getter ({@code getX}, or {@code isX} for a
 * {@code boolean}) or the field itself. Records are built through their canonical constructor;
 * classes through a constructor that takes every column field in declaration order, as Lombok's
 * {@code @AllArgsConstructor} does, or else through a constructor without parameters followed by
 * setters or field writes.
 */
public final class EntityReader {
	private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
			long.class, Long.class,
			int.class, Integer.class,
			boolean.class, Boolean.class
	);

	private EntityReader() {
	}

	/**
	 * Reads the table of an entity type.
	 *
	 * @param type entity type, annotated with {@link Table}
	 * @param <T>  entity type
	 * @return the entity's table
	 * @throws IllegalArgumentException when the type is not a valid entity, naming the type and
	 *                                  the field at fault
	 */
	public static <T> @NotNull EntityTable<T> read(@NotNull Class<T> type) {
		String table = tableName(type);
		List<Field> fields = fields(type);
		if (fields.isEmpty()) throw new IllegalArgumentException(type.getName() + " declares no column field");

		Map<String, Field> byName = new LinkedHashMap<>();
		List<EntityColumn<T, ?>> columns = new ArrayList<>();
		List<String> primaryKey = new ArrayList<>();
		List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
		for (Field field : fields) {
			EntityColumn<T, ?> column = column(type, field);
			byName.put(field.getName(), field);
			columns.add(column);
			if (field.isAnnotationPresent(Id.class)) {
				if (column.column().isNullable()) throw invalid(type, field, "a key field cannot be nullable");
				primaryKey.add(column.name());
			}

			References references = field.getAnnotation(References.class);
			if (references != null)
				foreignKeys.add(reference(type, field, column.name(), references));
		}

		for (ForeignKey foreignKey : type.getAnnotationsByType(ForeignKey.class))
			foreignKeys.add(foreignKey(type, byName, foreignKey));

		List<List<String>> uniqueKeys = new ArrayList<>();
		for (Unique unique : type.getAnnotationsByType(Unique.class))
			uniqueKeys.add(columnNames(type, byName, unique.value()));

		List<IndexDefinition> indexes = new ArrayList<>();
		for (Index index : type.getAnnotationsByType(Index.class)) {
			String[] columnNames = columnNames(type, byName, index.columns()).toArray(String[]::new);
			indexes.add(index.unique()
					? IndexDefinition.unique(index.name(), columnNames)
					: IndexDefinition.plain(index.name(), columnNames));
		}

		Function<Object[], T> instantiation = ReflectedEntityTable.instantiation(type, fields);
		try {
			return new ReflectedEntityTable<>(type, table, columns, builder -> {
				if (!primaryKey.isEmpty()) builder.primaryKey(primaryKey.toArray(String[]::new));
				uniqueKeys.forEach(columnNames -> builder.unique(columnNames.toArray(String[]::new)));
				foreignKeys.forEach(builder::foreignKey);
				indexes.forEach(builder::index);

				return builder;
			}, instantiation);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(type.getName() + ": " + e.getMessage(), e);
		}
	}

	private static String tableName(Class<?> type) {
		Table table = type.getAnnotation(Table.class);
		if (table == null) throw new IllegalArgumentException(type.getName() + " is not annotated with @" + Table.class.getSimpleName());

		return table.value();
	}

	private static String keyColumn(Class<?> type) {
		List<Field> keys = keyFields(type);
		if (keys.size() != 1)
			throw new IllegalArgumentException(type.getName() + " is referenced, so it needs exactly one @Id field, not " + keys.size());

		return columnName(keys.get(0));
	}

	/**
	 * Reads a foreign key of one field, to the key of an entity or to a column of a named table.
	 */
	private static ForeignKeyDefinition reference(Class<?> type, Field field, String column, References references) {
		boolean byEntity = references.entity() != void.class;
		boolean byTable = !references.table().isEmpty();
		if (byEntity == byTable)
			throw invalid(type, field, "@References " + (byEntity ? "names both an entity and a table; give one" : "names neither an entity nor a table"));
		if (byTable == references.column().isEmpty())
			throw invalid(type, field, "@References names a column together with a table, and only then");

		ForeignKeyDefinition.Builder builder = ForeignKeyDefinition.builder(column);
		if (byEntity) builder.references(tableName(references.entity()), keyColumn(references.entity()));
		else builder.references(references.table(), references.column());

		return builder.onDelete(references.onDelete()).build();
	}

	/**
	 * Reads a foreign key over several fields, which reference the referenced entity's key fields
	 * one by one, in their declaration order, or the named table's columns in the order given.
	 */
	private static ForeignKeyDefinition foreignKey(Class<?> type, Map<String, Field> fields, ForeignKey foreignKey) {
		String[] fieldNames = foreignKey.fields();
		boolean byEntity = foreignKey.entity() != void.class;
		boolean byTable = !foreignKey.table().isEmpty();
		if (byEntity == byTable)
			throw new IllegalArgumentException(type.getName() + ": the foreign key on " + List.of(fieldNames)
					+ (byEntity ? " names both an entity and a table; give one" : " names neither an entity nor a table"));
		if (byTable == (foreignKey.columns().length == 0))
			throw new IllegalArgumentException(type.getName() + ": the foreign key on " + List.of(fieldNames)
					+ " names columns together with a table, and only then");
		if (byTable) {
			if (fieldNames.length != foreignKey.columns().length)
				throw new IllegalArgumentException(type.getName() + ": the foreign key on " + List.of(fieldNames)
						+ " must name as many fields as columns of " + foreignKey.table() + ", not " + List.of(foreignKey.columns()));

			return ForeignKeyDefinition.builder(columnNames(type, fields, fieldNames).toArray(String[]::new))
					.references(foreignKey.table(), foreignKey.columns())
					.onDelete(foreignKey.onDelete())
					.build();
		}

		Class<?> referenced = foreignKey.entity();
		String referencedTable = tableName(referenced);
		List<Field> keys = keyFields(referenced);
		if (keys.isEmpty())
			throw new IllegalArgumentException(referenced.getName() + " is referenced by " + type.getName() + ", so it needs an @Id field");
		if (fieldNames.length != keys.size())
			throw new IllegalArgumentException(type.getName() + ": the foreign key on " + List.of(fieldNames) + " must name "
					+ keys.size() + " fields, one for each @Id field of " + referenced.getName());

		List<String> columns = columnNames(type, fields, fieldNames);
		for (int index = 0; index < keys.size(); index++) {
			Field field = fields.get(fieldNames[index]);
			Field key = keys.get(index);
			if (WRAPPERS.getOrDefault(field.getType(), field.getType()) != WRAPPERS.getOrDefault(key.getType(), key.getType()))
				throw invalid(type, field, "references " + referenced.getName() + "." + key.getName() + " of type "
						+ key.getType().getName() + ", not " + field.getType().getName());
		}

		return ForeignKeyDefinition.builder(columns.toArray(String[]::new))
				.references(referencedTable, keys.stream().map(EntityReader::columnName).toArray(String[]::new))
				.onDelete(foreignKey.onDelete())
				.build();
	}

	private static List<Field> keyFields(Class<?> type) {
		return fields(type).stream().filter(field -> field.isAnnotationPresent(Id.class)).toList();
	}

	static List<Field> fields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		if (type.isRecord()) {
			for (RecordComponent component : type.getRecordComponents())
				fields.add(declaredField(type, component.getName()));

			return fields;
		}

		for (Field field : type.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) continue;

			fields.add(field);
		}

		return fields;
	}

	private static Field declaredField(Class<?> type, String name) {
		try {
			return type.getDeclaredField(name);
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("Record " + type.getName() + " has no field for its component " + name, e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> EntityColumn<T, ?> column(Class<T> type, Field field) {
		Class<?> javaType = WRAPPERS.getOrDefault(field.getType(), field.getType());
		boolean nullable = nullable(field);
		if (nullable && field.getType().isPrimitive()) throw invalid(type, field, "a primitive field cannot be nullable");

		ColumnType columnType = columnType(type, field, javaType);
		ColumnDefinition.Builder schemaColumn = ColumnDefinition.builder(columnName(field), columnType);
		if (!nullable) schemaColumn.notNull();
		Default defaultValue = field.getAnnotation(Default.class);
		if (defaultValue != null) defaultValue(type, field, columnType, defaultValue.value(), schemaColumn);

		return EntityColumn.of((Class) javaType, schemaColumn.build(), accessor(type, field));
	}

	private static ColumnType columnType(Class<?> type, Field field, Class<?> javaType) {
		Length length = field.getAnnotation(Length.class);
		if (length != null && javaType != String.class) throw invalid(type, field, "@Length applies to String fields only");

		if (javaType == UUID.class) return ColumnType.uuid();
		if (javaType == String.class) return length == null ? ColumnType.text() : varchar(type, field, length.value());
		if (javaType == Long.class) return ColumnType.bigint();
		if (javaType == Integer.class) return ColumnType.integer();
		if (javaType == Boolean.class) return ColumnType.bool();
		if (javaType == Instant.class) return ColumnType.instant();
		if (javaType == LocalDate.class) return ColumnType.date();

		throw invalid(type, field, "type " + field.getType().getName()
				+ " has no column type; use UUID, String, long, int, boolean or their wrappers, Instant or LocalDate");
	}

	/**
	 * Gives a column the default of its field, read by the column's type.
	 */
	private static void defaultValue(Class<?> type, Field field, ColumnType columnType, String value, ColumnDefinition.Builder column) {
		if (columnType instanceof ColumnType.InstantType || columnType instanceof ColumnType.DateType)
			throw invalid(type, field, "@Default applies to UUID, text, number and boolean fields, not to " + field.getType().getSimpleName());
		if (columnType instanceof ColumnType.BoolType) {
			column.defaultValue(booleanDefault(type, field, value));
			return;
		}
		if (columnType instanceof ColumnType.BigintType || columnType instanceof ColumnType.IntegerType) {
			column.defaultValue(numberDefault(type, field, columnType, value));
			return;
		}
		if (columnType instanceof ColumnType.UuidType) {
			column.defaultValue(uuidDefault(type, field, value));
			return;
		}
		if (columnType instanceof ColumnType.VarcharType varchar && value.codePointCount(0, value.length()) > varchar.length())
			throw invalid(type, field, "@Default \"" + value + "\" is longer than the field's @Length(" + varchar.length() + ")");

		column.defaultValue(value);
	}

	private static boolean booleanDefault(Class<?> type, Field field, String value) {
		if (value.equals("true")) return true;
		if (value.equals("false")) return false;

		throw invalid(type, field, "@Default of a boolean field is true or false, not \"" + value + "\"");
	}

	private static long numberDefault(Class<?> type, Field field, ColumnType columnType, String value) {
		try {
			if (columnType instanceof ColumnType.IntegerType) return Integer.parseInt(value);
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw invalid(type, field, "@Default of a " + field.getType().getSimpleName() + " field is a decimal number in its range, not \"" + value + "\"");
		}
	}

	private static String uuidDefault(Class<?> type, Field field, String value) {
		try {
			UUID uuid = UUID.fromString(value);
			if (uuid.toString().equalsIgnoreCase(value)) return uuid.toString();
		} catch (IllegalArgumentException ignored) {
			// refused below
		}

		throw invalid(type, field, "@Default of a UUID field is a UUID in its 36-character form, not \"" + value + "\"");
	}

	private static ColumnType varchar(Class<?> type, Field field, int length) {
		try {
			return ColumnType.varchar(length);
		} catch (IllegalArgumentException e) {
			throw invalid(type, field, e.getMessage());
		}
	}

	private static boolean nullable(Field field) {
		return hasNullable(field.getAnnotations()) || hasNullable(field.getAnnotatedType().getAnnotations());
	}

	private static boolean hasNullable(Annotation[] annotations) {
		for (Annotation annotation : annotations)
			if (annotation.annotationType().getSimpleName().equals("Nullable")) return true;

		return false;
	}

	static String columnName(Field field) {
		Column column = field.getAnnotation(Column.class);
		if (column != null) return column.value();

		return field.getName()
				.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
				.replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
				.toLowerCase(Locale.ROOT);
	}

	private static List<String> columnNames(Class<?> type, Map<String, Field> fields, String[] fieldNames) {
		List<String> names = new ArrayList<>();
		for (String fieldName : fieldNames) {
			Field field = fields.get(fieldName);
			if (field == null) throw new IllegalArgumentException(type.getName() + " has no column field " + fieldName);

			names.add(columnName(field));
		}

		return names;
	}

	private static <T> Function<T, Object> accessor(Class<T> type, Field field) {
		Method method = type.isRecord() ? method(type, field.getName()) : getter(type, field);
		if (method != null) {
			method.setAccessible(true);
			return entity -> invoke(method, entity);
		}

		field.setAccessible(true);
		return entity -> {
			try {
				return field.get(entity);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Cannot read " + field, e);
			}
		};
	}

	private static Method getter(Class<?> type, Field field) {
		String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
		Method getter = method(type, "get" + suffix);
		if (getter == null && field.getType() == boolean.class) getter = method(type, "is" + suffix);

		return getter != null && getter.getReturnType() == field.getType() ? getter : null;
	}

	private static Method method(Class<?> type, String name) {
		try {
			return type.getDeclaredMethod(name);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private static Object invoke(Method method, Object entity) {
		try {
			return method.invoke(entity);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot call " + method, e);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException(method + " failed", e.getCause());
		}
	}

	private static IllegalArgumentException invalid(Class<?> type, Field field, String reason) {
		return new IllegalArgumentException(type.getName() + "." + field.getName() + ": " + reason);
	}
}
