package me.whereareiam.dialectica.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnsProcessorTest {
	@TempDir
	Path output;

	@Test
	void writesATypedConstantForEveryComponentOfARecord() throws IOException {
		Compilation compilation = compile(source("example.store.StoreEntry", """
				package example.store;

				import me.whereareiam.dialectica.entity.annotation.Column;
				import me.whereareiam.dialectica.entity.annotation.Id;
				import me.whereareiam.dialectica.entity.annotation.Length;
				import me.whereareiam.dialectica.entity.annotation.Nullable;
				import me.whereareiam.dialectica.entity.annotation.Table;

				import java.util.UUID;

				@Table("socialismus_store_entries")
				public record StoreEntry(
						@Id @Length(64) String storeName,
						@Id @Length(128) String entryKey,
						@Column("owner") UUID ownerId,
						@Nullable Long expiresAt,
						int revision,
						boolean pinned,
						String value
				) {
				}
				"""));

		assertTrue(compilation.succeeded(), compilation.toString());
		assertEquals("""
				package example.store;

				import me.whereareiam.dialectica.Dialectica;
				import me.whereareiam.dialectica.entity.EntityColumn;
				import me.whereareiam.dialectica.entity.EntityTable;

				import javax.annotation.processing.Generated;

				/**
				 * The typed columns of {@link StoreEntry}, for conditions, orders and updates.
				 * Written by Dialectica's annotation processor from the entity; do not edit.
				 */
				@Generated("me.whereareiam.dialectica.processor.ColumnsProcessor")
				public final class StoreEntryColumns {
				\tprivate static final EntityTable<StoreEntry> table = Dialectica.entity(StoreEntry.class);

				\t/**
				\t * The column {@code store_name}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, String> STORE_NAME = table.column("store_name", String.class);

				\t/**
				\t * The column {@code entry_key}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, String> ENTRY_KEY = table.column("entry_key", String.class);

				\t/**
				\t * The column {@code owner}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, java.util.UUID> OWNER = table.column("owner", java.util.UUID.class);

				\t/**
				\t * The column {@code expires_at}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, Long> EXPIRES_AT = table.column("expires_at", Long.class);

				\t/**
				\t * The column {@code revision}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, Integer> REVISION = table.column("revision", Integer.class);

				\t/**
				\t * The column {@code pinned}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, Boolean> PINNED = table.column("pinned", Boolean.class);

				\t/**
				\t * The column {@code value}.
				\t */
				\tpublic static final EntityColumn<StoreEntry, String> VALUE = table.column("value", String.class);

				\tprivate StoreEntryColumns() {
				\t}
				}
				""", compilation.generated("example/store/StoreEntryColumns.java"));
	}

	@Test
	void readsTheFieldsOfAClassAndNamesTheClassOfANestedEntity() throws IOException {
		Compilation compilation = compile(source("example.Outer", """
				package example;

				import me.whereareiam.dialectica.entity.annotation.Id;
				import me.whereareiam.dialectica.entity.annotation.Table;

				class Outer {
					@Table("example_sessions")
					static final class Session {
						private static final long TIMEOUT = 5;

						@Id private String sessionId;
						private transient String cached;
						private long lastSeenAt;
						private Boolean closed;
					}
				}
				"""));

		assertTrue(compilation.succeeded(), compilation.toString());
		String source = compilation.generated("example/OuterSessionColumns.java");
		assertTrue(source.contains("\nfinal class OuterSessionColumns {"), source);
		assertTrue(source.contains("EntityTable<Outer.Session> table = Dialectica.entity(Outer.Session.class);"), source);
		assertTrue(source.contains("EntityColumn<Outer.Session, String> SESSION_ID = table.column(\"session_id\", String.class);"), source);
		assertTrue(source.contains("EntityColumn<Outer.Session, Long> LAST_SEEN_AT = table.column(\"last_seen_at\", Long.class);"), source);
		assertTrue(source.contains("EntityColumn<Outer.Session, Boolean> CLOSED = table.column(\"closed\", Boolean.class);"), source);
		assertFalse(source.contains("TIMEOUT"), source);
		assertFalse(source.contains("CACHED"), source);
	}

	@Test
	void namesConstantsThatAColumnNameCannotBe() {
		assertEquals("ORDER", EntityColumns.constant("order"));
		assertEquals("LAST_SEEN_AT", EntityColumns.constant("last_seen_at"));
		assertEquals("_2FA_SECRET", EntityColumns.constant("2fa_secret"));
		assertEquals("DISPLAY_NAME", EntityColumns.constant("display-name"));
	}

	@Test
	void reportsAFieldWithoutAColumnType() throws IOException {
		Compilation compilation = compile(source("example.Event", """
				package example;

				import me.whereareiam.dialectica.entity.annotation.Table;

				@Table("example_events")
				public record Event(String name, java.time.Instant happenedAt) {
				}
				"""));

		assertFalse(compilation.succeeded());
		assertTrue(compilation.errors().contains("Field happenedAt of type java.time.Instant has no column type; use UUID, String, long, int or boolean or their wrappers"),
				compilation.toString());
	}

	@Test
	void reportsColumnsThatShareAConstantAndEntitiesItCannotDescribe() throws IOException {
		Compilation compilation = compile(source("example.Broken", """
				package example;

				import me.whereareiam.dialectica.entity.annotation.Column;
				import me.whereareiam.dialectica.entity.annotation.Table;

				public class Broken {
					@Table("example_clashes")
					public record Clash(String displayName, @Column("DISPLAY_NAME") String shown) {
					}

					@Table("example_generics")
					public record Generic<T>(String name) {
					}

					@Table("example_hidden")
					private record Hidden(String name) {
					}

					@Table("example_inner")
					public class Inner {
						private String name;
					}

					@Table("example_empty")
					public record Empty() {
					}
				}
				"""));

		assertFalse(compilation.succeeded());
		String errors = compilation.errors();
		assertTrue(errors.contains("Columns of displayName and shown would both be the constant DISPLAY_NAME"), errors);
		assertTrue(errors.contains("A generic entity has no typed columns; Generic declares type parameters"), errors);
		assertTrue(errors.contains("The columns of Hidden cannot be written: it is private"), errors);
		assertTrue(errors.contains("The columns of Inner cannot be written: it is an inner class; make it static"), errors);
		assertTrue(errors.contains("Empty declares no column field"), errors);
	}

	private Compilation compile(JavaFileObject... sources) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		Path generated = Files.createDirectories(output.resolve("generated"));
		Path classes = Files.createDirectories(output.resolve("classes"));
		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
			List<String> options = List.of(
					"-classpath", System.getProperty("java.class.path"),
					"-implicit:class",
					"-s", generated.toString(),
					"-d", classes.toString()
			);
			JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, options, null, List.of(sources));
			task.setProcessors(List.of(new ColumnsProcessor()));

			return new Compilation(task.call(), diagnostics.getDiagnostics(), generated);
		}
	}

	private static JavaFileObject source(String name, String code) {
		return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return code;
			}
		};
	}

	/**
	 * What a compilation reported and the sources the processor wrote.
	 */
	private record Compilation(boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path generated) {
		String generated(String path) throws IOException {
			return Files.readString(generated.resolve(path));
		}

		String errors() {
			StringBuilder errors = new StringBuilder();
			for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics)
				if (diagnostic.getKind() == Diagnostic.Kind.ERROR) errors.append(diagnostic.getMessage(null)).append('\n');

			return errors.toString();
		}

		@Override
		public String toString() {
			return diagnostics.toString();
		}
	}
}
