package me.whereareiam.dialectica.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Writes the source of the {@code <Entity>Columns} class of an entity.
 */
final class ColumnsWriter {
	private ColumnsWriter() {
	}

	/**
	 * Returns the simple name of the class of an entity's columns: the entity's name with
	 * {@code Columns} appended, after the names of the types it is nested in.
	 */
	static String className(TypeElement type) {
		StringBuilder name = new StringBuilder(type.getSimpleName());
		for (Element enclosing = type.getEnclosingElement(); enclosing instanceof TypeElement outer; enclosing = outer.getEnclosingElement())
			name.insert(0, outer.getSimpleName());

		return name + "Columns";
	}

	static String write(EntityColumns columns) {
		TypeElement type = columns.type();
		String packageName = packageOf(type).getQualifiedName().toString();
		String entity = entityName(type, packageName);
		String visibility = isPublic(type) ? "public " : "";

		StringBuilder source = new StringBuilder();
		if (!packageName.isEmpty()) source.append("package ").append(packageName).append(";\n\n");
		source.append("import me.whereareiam.dialectica.Dialectica;\n")
				.append("import me.whereareiam.dialectica.entity.EntityColumn;\n")
				.append("import me.whereareiam.dialectica.entity.EntityTable;\n\n")
				.append("import javax.annotation.processing.Generated;\n\n")
				.append("/**\n")
				.append(" * The typed columns of {@link ").append(entity).append("}, for conditions, orders and updates.\n")
				.append(" * Written by Dialectica's annotation processor from the entity; do not edit.\n")
				.append(" */\n")
				.append("@Generated(\"").append(ColumnsProcessor.class.getName()).append("\")\n")
				.append(visibility).append("final class ").append(className(type)).append(" {\n")
				.append("\tprivate static final EntityTable<").append(entity).append("> table = Dialectica.entity(").append(entity).append(".class);\n");
		for (EntityColumns.Column column : columns.columns())
			source.append("\n\t/**\n")
					.append("\t * The column {@code ").append(column.name().replace("*/", "*&#47;")).append("}.\n")
					.append("\t */\n")
					.append("\tpublic static final EntityColumn<").append(entity).append(", ").append(column.valueType()).append("> ")
					.append(column.constant()).append(" = table.column(\"").append(escape(column.name())).append("\", ")
					.append(column.valueType()).append(".class);\n");
		source.append("\n\tprivate ").append(className(type)).append("() {\n\t}\n}\n");

		return source.toString();
	}

	private static String entityName(TypeElement type, String packageName) {
		String qualified = type.getQualifiedName().toString();

		return packageName.isEmpty() ? qualified : qualified.substring(packageName.length() + 1);
	}

	private static boolean isPublic(TypeElement type) {
		for (Element element = type; element instanceof TypeElement nested; element = nested.getEnclosingElement())
			if (!nested.getModifiers().contains(Modifier.PUBLIC)) return false;

		return true;
	}

	private static PackageElement packageOf(Element element) {
		Element current = element;
		while (!(current instanceof PackageElement))
			current = current.getEnclosingElement();

		return (PackageElement) current;
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
