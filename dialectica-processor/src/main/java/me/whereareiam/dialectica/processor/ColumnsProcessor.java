package me.whereareiam.dialectica.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Writes, for every entity annotated with Dialectica's {@code @Table}, a class
 * {@code <Entity>Columns} in the entity's package that holds one typed {@code EntityColumn}
 * constant per column, named after the column in upper snake case:
 * <pre>{@code
 * @Table("socialismus_store_entries")
 * public record StoreEntry(@Id String storeName, @Id String entryKey, @Nullable Long expiresAt, String value) {
 * }
 *
 * // written by the processor
 * public final class StoreEntryColumns {
 *     public static final EntityColumn<StoreEntry, String> STORE_NAME = ...;
 *     public static final EntityColumn<StoreEntry, String> ENTRY_KEY = ...;
 *     public static final EntityColumn<StoreEntry, Long> EXPIRES_AT = ...;
 *     public static final EntityColumn<StoreEntry, String> VALUE = ...;
 * }
 * }</pre>
 * A project adds the processor to the compiler, in Gradle as
 * {@code annotationProcessor("me.whereareiam:dialectica-processor:<version>")}. The columns are the
 * ones {@code Dialectica.entity(StoreEntry.class)} reads: the record's components or the class's
 * fields that are neither static nor transient, named by {@code @Column} or else in snake case. The
 * written class reads the entity's table once, when it is first used, so it needs Dialectica's
 * entry point, the {@code dialectica} module, at runtime.
 * <p>
 * The processor reports an entity it cannot describe as a compile error: a generic, private or
 * inner entity, a field of a type that has no column type, or two columns whose constants would
 * share a name.
 */
public final class ColumnsProcessor extends AbstractProcessor {
	private static final String TABLE = "me.whereareiam.dialectica.entity.annotation.Table";

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of(TABLE);
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
		TypeElement table = processingEnv.getElementUtils().getTypeElement(TABLE);
		if (table == null || annotations.isEmpty()) return false;

		for (Element element : round.getElementsAnnotatedWith(table))
			write(element);

		return false;
	}

	private void write(Element element) {
		Messager messager = processingEnv.getMessager();
		if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
			messager.printMessage(Diagnostic.Kind.ERROR, "@Table applies to a record or a class", element);
			return;
		}

		TypeElement type = (TypeElement) element;
		EntityColumns columns = EntityColumns.read(type, processingEnv.getElementUtils());
		if (!columns.problems().isEmpty()) {
			columns.problems().forEach(problem -> messager.printMessage(Diagnostic.Kind.ERROR, problem.message(), problem.element()));
			return;
		}

		create(type, ColumnsWriter.write(columns));
	}

	private void create(TypeElement type, String source) {
		Filer filer = processingEnv.getFiler();
		String name = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName() + "." + ColumnsWriter.className(type);
		try {
			JavaFileObject file = filer.createSourceFile(name, type);
			try (Writer writer = file.openWriter()) {
				writer.write(source);
			}
		} catch (FilerException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot write " + name + ": " + e.getMessage(), type);
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot write " + name + ": " + e, type);
		}
	}
}
