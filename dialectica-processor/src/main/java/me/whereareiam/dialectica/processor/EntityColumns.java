package me.whereareiam.dialectica.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The columns of an entity as the processor reads them from its source: the fields that
 * {@code EntityReader} turns into columns, with their column names, the constants named after
 * them, and the Java types of their values.
 */
final class EntityColumns {
	private static final String COLUMN = "me.whereareiam.dialectica.entity.annotation.Column";
	private static final Map<String, String> VALUE_TYPES = Map.of(
			"long", "Long",
			"int", "Integer",
			"boolean", "Boolean",
			"java.lang.Long", "Long",
			"java.lang.Integer", "Integer",
			"java.lang.Boolean", "Boolean",
			"java.lang.String", "String",
			"java.util.UUID", "java.util.UUID",
			"java.time.Instant", "java.time.Instant",
			"java.time.LocalDate", "java.time.LocalDate"
	);

	private final TypeElement type;
	private final List<Column> columns;
	private final List<Problem> problems;

	private EntityColumns(TypeElement type, List<Column> columns, List<Problem> problems) {
		this.type = type;
		this.columns = List.copyOf(columns);
		this.problems = List.copyOf(problems);
	}

	/**
	 * Reads the columns of an entity, collecting every problem that keeps the processor from
	 * describing it.
	 */
	static EntityColumns read(TypeElement type, Elements elements) {
		List<Problem> problems = new ArrayList<>(shapeProblems(type));
		List<Column> columns = new ArrayList<>();
		Map<String, VariableElement> constants = new HashMap<>();
		for (Element member : type.getEnclosedElements()) {
			if (!isColumn(member)) continue;

			VariableElement field = (VariableElement) member;
			String valueType = VALUE_TYPES.get(typeName(field.asType()));
			String name = columnName(field, elements);
			String constant = constant(name);
			VariableElement taken = constants.putIfAbsent(constant, field);
			if (valueType == null)
				problems.add(new Problem(field, "Field " + field.getSimpleName() + " of type " + field.asType()
						+ " has no column type; use UUID, String, long, int, boolean or their wrappers, Instant or LocalDate"));
			if (taken != null)
				problems.add(new Problem(field, "Columns of " + taken.getSimpleName() + " and " + field.getSimpleName()
						+ " would both be the constant " + constant));

			columns.add(new Column(name, constant, valueType));
		}
		if (columns.isEmpty()) problems.add(new Problem(type, type.getSimpleName() + " declares no column field"));

		return new EntityColumns(type, columns, problems);
	}

	TypeElement type() {
		return type;
	}

	List<Column> columns() {
		return columns;
	}

	List<Problem> problems() {
		return problems;
	}

	/**
	 * Turns a column name into the name of its constant: {@code store_name} into
	 * {@code STORE_NAME}. Characters a Java name cannot hold become underscores.
	 */
	static String constant(String column) {
		String constant = column.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
		if (constant.isEmpty() || Character.isDigit(constant.charAt(0))) return "_" + constant;

		return constant;
	}

	/**
	 * Names a field's column as {@code EntityReader} does: by {@code @Column}, or else the field's
	 * name in snake case.
	 */
	private static String columnName(VariableElement field, Elements elements) {
		for (AnnotationMirror annotation : field.getAnnotationMirrors()) {
			if (!((TypeElement) annotation.getAnnotationType().asElement()).getQualifiedName().contentEquals(COLUMN)) continue;

			for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> value : elements.getElementValuesWithDefaults(annotation).entrySet())
				if (value.getKey().getSimpleName().contentEquals("value")) return (String) value.getValue().getValue();
		}

		return field.getSimpleName().toString()
				.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
				.replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
				.toLowerCase(Locale.ROOT);
	}

	private static boolean isColumn(Element member) {
		if (member.getKind() != ElementKind.FIELD) return false;

		return !member.getModifiers().contains(Modifier.STATIC) && !member.getModifiers().contains(Modifier.TRANSIENT);
	}

	private static List<Problem> shapeProblems(TypeElement type) {
		List<Problem> problems = new ArrayList<>();
		if (!type.getTypeParameters().isEmpty())
			problems.add(new Problem(type, "A generic entity has no typed columns; " + type.getSimpleName() + " declares type parameters"));
		if (type.getModifiers().contains(Modifier.PRIVATE))
			problems.add(new Problem(type, "The columns of " + type.getSimpleName() + " cannot be written: it is private"));
		if (type.getNestingKind() == NestingKind.MEMBER && !type.getModifiers().contains(Modifier.STATIC) && type.getKind() != ElementKind.RECORD)
			problems.add(new Problem(type, "The columns of " + type.getSimpleName() + " cannot be written: it is an inner class; make it static"));
		if (type.getNestingKind() == NestingKind.LOCAL || type.getNestingKind() == NestingKind.ANONYMOUS)
			problems.add(new Problem(type, "The columns of " + type.getSimpleName() + " cannot be written: it is a local class"));

		return problems;
	}

	private static String typeName(TypeMirror type) {
		if (type.getKind() != TypeKind.DECLARED) return type.toString();

		return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
	}

	/**
	 * A column of the entity.
	 *
	 * @param name      name of the column
	 * @param constant  name of its constant
	 * @param valueType Java type of its values, as written in source, or {@code null} for a field
	 *                  without a column type
	 */
	record Column(String name, String constant, String valueType) {
	}

	/**
	 * A reason the processor cannot describe an entity, and the element at fault.
	 */
	record Problem(Element element, String message) {
	}
}
