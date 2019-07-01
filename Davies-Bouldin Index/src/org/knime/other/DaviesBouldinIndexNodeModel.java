package org.knime.other;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.MissingValue;
import org.knime.core.data.MissingValueException;
import org.knime.core.data.RowIterator;
import org.knime.core.data.RowKey;
import static org.knime.core.data.RowKey.createRowKey;
import org.knime.core.data.container.DataContainer;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * This is the model implementation of Davies-Bouldin Index. This node computes
 * the Davies-Bouldin index for evaluating clustering performance on a dataset.
 *
 * @author Rytis Kumpa
 */
public class DaviesBouldinIndexNodeModel extends NodeModel {

	// Configuration keys for retrieving the settings values
	static final String CFGKEY_FILTER = "Include columns";
	static final String CFGKEY_CLUSTER = "Cluster allocation";
	static final String CFGKEY_RANDOM = "Random sampling";
	static final String CFGKEY_SAMPLERATE = "Sample rate";
	static final String CFGKEY_STATE = "Random state";

	// Settings models
	private final SettingsModelColumnName m_clusterColumn = new SettingsModelColumnName(CFGKEY_CLUSTER, "");

	private final SettingsModelFilterString m_filterColumns = new SettingsModelFilterString(CFGKEY_FILTER);

	private final SettingsModelBoolean m_randomSampling = new SettingsModelBoolean(CFGKEY_RANDOM, true);

	private final SettingsModelDoubleBounded m_sampleRate = new SettingsModelDoubleBounded(CFGKEY_SAMPLERATE, 0.5, 0.0,
			1.0);

	private final SettingsModelInteger m_randomState = new SettingsModelInteger(CFGKEY_STATE, 0);

	// Data containers for storing cluster information.
	private HashMap<String, DataContainer> clusters = new HashMap<String, DataContainer>();

	private HashMap<String, ArrayList<Double>> clusterCentroids = new HashMap<String, ArrayList<Double>>();

	/**
	 * Constructor for the node model.
	 */
	protected DaviesBouldinIndexNodeModel() {
		super(1, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {

		DataTableSpec inTableSpec = inData[0].getDataTableSpec();

		DataTableSpec outTableSpec = configure(new DataTableSpec[] { inTableSpec })[0];

		BufferedDataContainer outputContainer = exec.createDataContainer(outTableSpec);

		clusters = new HashMap<String, DataContainer>();

		clusterCentroids = new HashMap<String, ArrayList<Double>>();

		Random rand = new java.util.Random(m_randomState.getIntValue());

		int clusterNameColumnID = inTableSpec.columnsToIndices(m_clusterColumn.getStringValue())[0];
		int[] includeColumnID = inTableSpec
				.columnsToIndices((String[]) m_filterColumns.getIncludeList().stream().toArray(String[]::new));

		// The data is read and processed accordingly.
		RowIterator rowIterator = inData[0].iterator();
		while (rowIterator.hasNext()) {
			exec.checkCanceled();
			exec.setProgress(0.05 / 1.0, "Reading Data.");
			double randomThreshold = 0.0;
			DataRow currentRow = rowIterator.next();
			if (m_randomSampling.getBooleanValue()) {
				randomThreshold = m_sampleRate.getDoubleValue();
			} else {
				randomThreshold = 1.0;
			}
			// Only if the random value is lower than the threshold, we process the row.
			if (randomThreshold >= rand.nextDouble()) {
				DataCell[] newRow = new DataCell[includeColumnID.length];
				String clusterName = currentRow.getCell(clusterNameColumnID).toString();
				if (clusters.containsKey(clusterName)) {
					for (int i = 0; i < includeColumnID.length; i++) {
						DataCell cell = currentRow.getCell(includeColumnID[i]);
						if (!cell.isMissing()) {
							newRow[i] = cell;
						} else {
							throw new MissingValueException((MissingValue) cell);
						}

					}
					RowKey rowKey = createRowKey(clusters.get(clusterName).size());
					clusters.get(clusterName).addRowToTable(new DefaultRow(rowKey, newRow));
				} else {
					DataContainer container = exec.createDataContainer(createDataTableSpecs(inTableSpec));
					clusters.put(clusterName, container);
					for (int i = 0; i < includeColumnID.length; i++) {
						DataCell cell = currentRow.getCell(includeColumnID[i]);
						if (!cell.isMissing()) {
							newRow[i] = cell;
						} else {
							throw new MissingValueException((MissingValue) cell);
						}

					}
					RowKey rowKey = createRowKey(clusters.get(clusterName).size());
					clusters.get(clusterName).addRowToTable(new DefaultRow(rowKey, newRow));
				}
			}
			exec.setProgress(0.1 / 1.0, "Data read.");
		}

		// DataContainer has to be closed so that the stored information can be
		// retrieved.
		for (String cluster : clusters.keySet()) {
			clusters.get(cluster).close();
		}

		// Cluster centroids are computed, so that closest clusters are found.
		if (clusters.size() > 1) {
			computeClusterCentroids();
		} else {
			throw new Exception("There have to be at least two clusters for Davies-Bouldin index to be computed.");
		}

		double DB = 0.0;

		// Main loop for computing the Davies-Bouldin Index.
		int i = 0;
		for (String cluster : clusters.keySet()) {

			exec.checkCanceled();
			exec.setProgress((double) i / (double) clusters.size(), "Processing cluster: " + i);
			i++;

			double maxR = Double.MIN_VALUE;
			Set<String> otherClusters = clusters.keySet().stream().collect(Collectors.toSet());
			otherClusters.remove(cluster);
			double S1 = computeScatterMeasure(clusterCentroids.get(cluster), clusters.get(cluster));

			for (String otherCluster : otherClusters) {

				exec.checkCanceled();

				double S2 = computeScatterMeasure(clusterCentroids.get(otherCluster), clusters.get(otherCluster));
				double M = clusterSeparationMeasure(clusterCentroids.get(cluster), clusterCentroids.get(otherCluster));
				double R = (S1 + S2) / M;
				if (maxR < R) {
					maxR = R;
				}
			}
			DB += maxR;
		}

		DB = DB / (double) clusters.size();

		DataCell DBCell = new DoubleCell(DB);

		outputContainer.addRowToTable(new DefaultRow("Row_1", DBCell));

		outputContainer.close();

		clusterCentroids = null;
		clusters = null;

		return new BufferedDataTable[] { outputContainer.getTable() };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
		clusters = new HashMap<String, DataContainer>();
		clusterCentroids = new HashMap<String, ArrayList<Double>>();
	}

	/**
	 * Computes the Euclidian Distance for two points.
	 * 
	 * @param p1 - An ArrayList with double values for the point.
	 * @param p2 - An ArrayList with double values for the other point.
	 * @return Returns the distance as a double value.
	 */
	private double euclidianDistance(ArrayList<Double> p1, ArrayList<Double> p2) {

		double distance = 0.0;

		for (int i = 0; i < p1.size(); i++) {
			double temp = p2.get(i) - p1.get(i);
			distance += Math.pow(temp, 2.0);
		}

		return Math.sqrt(distance);
	}

	/**
	 * Computes the cluster centroids for currently existing DataContainer in
	 * clusters HashMap.
	 */
	private void computeClusterCentroids() {

		for (String cluster : clusters.keySet().stream().toArray(String[]::new)) {

			Iterator<DataRow> iter = clusters.get(cluster).getTable().iterator();

			ArrayList<Double> center = iter.next().stream().map(x -> ((DoubleValue) x).getDoubleValue())
					.collect(Collectors.toCollection(ArrayList::new));

			while (iter.hasNext()) {
				ArrayList<Double> point = iter.next().stream().map(x -> ((DoubleValue) x).getDoubleValue())
						.collect(Collectors.toCollection(ArrayList::new));
				for (int i = 0; i < point.size(); i++) {
					center.set(i, point.get(i) + center.get(i));
				}
			}

			center = center.stream().map(x -> (Double) x / clusters.get(cluster).size())
					.collect(Collectors.toCollection(ArrayList::new));

			clusterCentroids.put(cluster, center);
		}
	}

	/**
	 * Computes the scatter measure for the particular cluster, given its centroid.
	 * 
	 * @param centroid The centroid of the cluster.
	 * @param cluster  All of the points in the cluster stored in a DataContainer.
	 * @return Returns the computed scatter measure.
	 */
	private double computeScatterMeasure(ArrayList<Double> centroid, DataContainer cluster) {

		RowIterator iterator = cluster.getTable().iterator();
		double dist = 0.0;
		long size = cluster.size();

		while (iterator.hasNext()) {
			ArrayList<Double> point = iterator.next().stream().map(x -> ((DoubleValue) x).getDoubleValue())
					.collect(Collectors.toCollection(ArrayList::new));
			dist += euclidianDistance(centroid, point);
		}

		return dist / (double) size;
	}

	/**
	 * The sepation measure of two clusters. It can be computed by calculating the
	 * Euclidian distance between the centroind of the clusters.
	 * 
	 * @param c1 The centroid of the first cluster.
	 * @param c2 The centroid of the second cluster.
	 * @return The double value of the separation measure.
	 */
	private double clusterSeparationMeasure(ArrayList<Double> c1, ArrayList<Double> c2) {
		return euclidianDistance(c1, c2);
	}

	/**
	 * Creates the DataTableSpec for the Data Containers that will be used for
	 * computing the Silhouette Coefficient.
	 * 
	 * @param spec The DataTableSpec of the input table.
	 * @return Returns the DataTableSpec of the table.
	 */
	private DataTableSpec createDataTableSpecs(DataTableSpec spec) {

		DataTableSpecCreator specCreator = new DataTableSpecCreator();

		for (String column : m_filterColumns.getIncludeList()) {
			specCreator.addColumns(new DataColumnSpecCreator(column, DoubleCell.TYPE).createSpec());
		}

		return specCreator.createSpec();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		if (m_filterColumns.getIncludeList().isEmpty()) {
			throw new InvalidSettingsException("No numeric columns selected from input.");
		}

		DataColumnSpec[] colSpecs = new DataColumnSpec[1];
		colSpecs[0] = new DataColumnSpecCreator("Davies-Bouldin Index", DoubleCell.TYPE).createSpec();
		DataTableSpec outSpec = new DataTableSpec(colSpecs);

		return new DataTableSpec[] { outSpec };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {

		m_clusterColumn.saveSettingsTo(settings);
		m_filterColumns.saveSettingsTo(settings);
		m_randomState.saveSettingsTo(settings);
		m_randomSampling.saveSettingsTo(settings);
		m_sampleRate.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {

		m_clusterColumn.loadSettingsFrom(settings);
		m_filterColumns.loadSettingsFrom(settings);
		m_randomSampling.loadSettingsFrom(settings);
		m_randomState.loadSettingsFrom(settings);
		m_sampleRate.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

		m_clusterColumn.validateSettings(settings);
		m_filterColumns.validateSettings(settings);
		m_randomSampling.validateSettings(settings);
		m_randomState.validateSettings(settings);
		m_sampleRate.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

	}

}
