package cz.it4i.fiji.datastore.bdv_server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Comparator;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import cz.it4i.fiji.datastore.register_service.Dataset;
import cz.it4i.fiji.datastore.register_service.DatasetRepository;
import cz.it4i.fiji.datastore.register_service.DatasetVersion;
import io.smallrye.mutiny.Uni;
import lombok.extern.log4j.Log4j2;

/**
 * Provides a list of available datasets on this {@link BigDataServer}
 *
 * @author HongKee Moon &lt;moon@mpi-cbg.de&gt; adapted by Jan Kožusznik
 *         &lt;jan.kozusznik@vsb.cz&gt;
 */
@SuppressWarnings("javadoc")
@Log4j2
@ApplicationScoped
class JsonDatasetListHandlerTS 
{

	@Inject
	DatasetRepository datasetRepository;

	public Uni<JsonObject> run(String uuid, URI baseURI)
		throws IOException
	{
		return run(uuid, baseURI, false);
	}

	public Uni<JsonObject> run(String uuid, URI baseURI,	boolean allVersionsInOne) throws IOException
	{
		return list(uuid, baseURI, allVersionsInOne);
	}

	public void writeInfoAboutVersion(Dataset dataset, final JsonWriter writer,
		URI baseURI, String version) throws IOException
	{
		final String datasetName = "dataset:" + dataset.getUuid() + ", version:" +
			version;
	
		writer.name(datasetName).beginObject();
	
		writer.name("id").value(datasetName);
	
		// writer.name( "desc" ).value( contextHandler.getDescription() );
		writer.name("description").value("NotImplemented");
		boolean endsWithSlash = baseURI.toString().endsWith("/");
		writer.name("datasetUrl").value(baseURI.resolve((endsWithSlash ? "../"
			: "./") + version)
			.toString());
		writer.name("thumbnailUrl").value(baseURI.resolve((endsWithSlash ? "../"
			: "./") + version + "/png").toString());
	
		writer.endObject();
	}

	private Uni<JsonObject> list(String uuid, URI baseURI, boolean allVersionsInOne)
		throws IOException
	{
		return datasetRepository.findByUUID(uuid).
				onItem().transform(dataset -> {
					try {
						return getJsonDatasetList(dataset, baseURI, allVersionsInOne);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
	}

	private JsonObject getJsonDatasetList(Dataset dataset, URI baseURI, boolean allVersionsInOne)
		throws IOException
	{
		StringWriter out = new StringWriter();

		try (final JsonWriter writer = new JsonWriter(out)) {

			writer.setIndent("\t");

			writer.beginObject();

			getContexts(dataset, writer, baseURI, allVersionsInOne);

			writer.endObject();

			writer.flush();

		}
		return JsonParser.parseString(out.toString()).getAsJsonObject();
	}

	private String getContexts(Dataset dataset, final JsonWriter writer,
		URI baseURI, boolean allVersionsInOne)
		throws IOException
	{
		final StringBuilder sb = new StringBuilder();
		if (!allVersionsInOne) {
			for (final DatasetVersion datasetVersion : dataset.getDatasetVersion()
				.stream().sorted(Comparator.comparingInt(DatasetVersion::getValue))
				.collect(Collectors.toList()))
			{

				writeInfoAboutVersion(dataset, writer, baseURI, Integer.toString(
					datasetVersion.getValue()));
			}
		}
		else {
			if (!dataset.getDatasetVersion().isEmpty()) {
				writeInfoAboutVersion(dataset, writer, baseURI, "all");
			}
		}
		if (!dataset.getDatasetVersion().isEmpty()) {
			writeInfoAboutVersion(dataset, writer, baseURI, "mixedLatest");
		}
		return sb.toString();
	}
}
