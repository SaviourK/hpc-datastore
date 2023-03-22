/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2020 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.register_service;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.quarkus.panache.common.Parameters;

import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;

import cz.it4i.fiji.datastore.ApplicationConfiguration;
import cz.it4i.fiji.datastore.DatasetHandler;
import io.smallrye.mutiny.Uni;
import lombok.extern.log4j.Log4j2;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;

/*import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.Repository;*/

//@Repository(forEntity = Dataset.class)

@Log4j2
@Default
@ApplicationScoped
public class DatasetRepository implements PanacheRepository<Dataset>,
	Serializable
{

	private static final long serialVersionUID = 7503192760728646786L;

	@Inject
	ApplicationConfiguration configuration;

	public Uni<Dataset> findByUUID(String uuid) {
		//TODO fix
		return find("SELECT ds " +
						"FROM Dataset ds " +
						"LEFT JOIN FETCH ds.resolutionLevel " +
						"WHERE ds.uuid = :uuid",
				Parameters.with("uuid",
						uuid)).singleResult()
				.onItem().transform(resultDataset -> {
					DatasetHandler dfh = configuration.getDatasetHandler(uuid);
					try {
						resolveVersion(dfh, resultDataset);
						resolveLabel(resultDataset);
						resolveViewSetups(resultDataset);
						return resultDataset;
					} catch (IOException | SpimDataException exc) {
						throw new RuntimeException(exc);
					}
				});
	}

	public Uni<DatasetVersion> findByUUIDVersion(String uuid, int version) {
		return findByUUID(uuid).onItem().transform(dataset -> {
			Optional<DatasetVersion> result;
			if (version == -1) {
				result = dataset.getDatasetVersion().stream().max(Comparator.comparing(
						DatasetVersion::getValue));
			}
			else {
				result = dataset.getDatasetVersion().stream().filter(v -> v
						.getValue() == version).findAny();
			}
			if (result.isEmpty()) {
				throw new NotFoundException("Dataset with UUID = " + uuid +
						" has no version " + version);
			}
			return result.get();
		});

	}

	@Override
	@ReactiveTransactional
	public Uni<Dataset> persist(Dataset entity) {
		return PanacheRepository.super.persist(entity)
				.onItem().transform(dataset -> {
					configuration.getDatasetHandler(entity.getUuid()).setLabel(entity
							.getLabel());
					return dataset;
				});
	}

	private void resolveVersion(DatasetHandler dh,
		Dataset resultDataset) throws IOException
	{
		resultDataset.setDatasetVersion(dh.getAllVersions().stream().map(
			v -> new DatasetVersion(v)).collect(Collectors.toList()));
	}

	private void resolveLabel(Dataset dataset) {
		try {
			DatasetHandler dh = configuration.getDatasetHandler(dataset.getUuid());
			String label = dh.getLabel();
			dataset.setLabel(label);
		}
		catch (Exception exc) {
			log.warn("resolve label", exc);
			
		}
	}

	private void resolveViewSetups(Dataset dataset)
		throws SpimDataException
	{
		SpimData spimData = configuration.getDatasetHandler(dataset.getUuid())
			.getSpimData();
		dataset.setViewSetup(spimData.getSequenceDescription()
			.getViewSetupsOrdered().stream().map(vs -> ViewSetup.builder().index(vs
				.getId()).angleId(vs.getAngle().getId()).channelId(vs.getChannel()
					.getId()).build()).collect(Collectors.toList()));
	}
}
