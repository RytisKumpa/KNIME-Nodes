package org.knime.SilhouetteCoeffiecient;

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
import org.knime.core.data.DataTable;
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
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;

/**
 * This is the model implementation of SilhouetteCoefficient. This node computes
 * the Silhouette Coefficient for the provided clustering result.
 *
 * @author Rytis Kumpa
 */
public class SilhouetteCoefficientNodeModel extends NodeModel {
	
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

	private final SettingsModelInteger m_randomState = new SettingsModelInteger(
			SilhouetteCoefficientNodeModel.CFGKEY_STATE, 0);

	// Data containers for storing cluster information.
	private HashMap<String, DataContainer> clusters = new HashMap<String, DataContainer>();

	private HashMap<String, ArrayList<Double>> clusterCentroids = new HashMap<String, ArrayList<Double>>();

	/**
	 * Constructor for the node model.
	 */
	protected SilhouetteCoefficientNodeModel() {
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
		
		// DataContainer has to be closed so that the stored information can be retrieved.
		for (String cluster : clusters.keySet()) {
			clusters.get(cluster).close();
		}
		
		// Cluster Centroids are computed, so that closest clusters are found.
		if (clusters.size() > 1) {
			computeClusterCentroids();
		} else {
			throw new Exception("There have to be at least two clusters for Silhouette Coefficient to be computed.");
		}

		HashMap<String, String> closestCluster = new HashMap<String, String>();

		closestCluster = computeClosestClusters();

		double clusterInDistance = 0.0;
		double clusterOutDistance = 0.0;
		int i = 0;
		
		// Main loop, where the Silhouette Coefficient is computed.
		for (String cluster : clusters.keySet()) {
			
			exec.setProgress((double) i / (double) clusters.size(), "Processing cluster: " + i);
			exec.checkCanceled();
			
			double currentClusterInDistance = 0.0;
			DataTable clusterTable = clusters.get(cluster).getTable();
			RowIterator iterator = clusterTable.iterator();
			long clusterSize = 0;
			boolean clusterSizeCounted = false;
			
			// Inter-cluster distance is computed for each sample.
			while (iterator.hasNext()) {
				
				exec.checkCanceled();
				
				DataRow row1 = iterator.next();
				ArrayList<Double> row1Doubles = row1.stream().map(x -> ((DoubleValue) x).getDoubleValue())
						.collect(Collectors.toCollection(ArrayList::new));
				RowIterator secondIterator = clusterTable.iterator();
				
				while (secondIterator.hasNext()) {
					DataRow row2 = secondIterator.next();
					ArrayList<Double> row2Doubles = row2.stream().map(x -> ((DoubleValue) x).getDoubleValue())
							.collect(Collectors.toCollection(ArrayList::new));
					if (!clusterSizeCounted) {
						clusterSize++;
					}
					currentClusterInDistance += euclidianDistance(row1Doubles, row2Doubles);
				}
				clusterSizeCounted = true;
				currentClusterInDistance = currentClusterInDistance / clusterSize;

			}
			currentClusterInDistance = currentClusterInDistance / clusterSize;
			clusterInDistance += currentClusterInDistance;

			double currentClusterOutDistance = 0.0;
			iterator = clusterTable.iterator();
			DataTable closestClusterTable = clusters.get(closestCluster.get(cluster)).getTable();
			long closestClusterSize = 0;
			boolean closestClusterSizeCounted = false;
			
			// Intra-cluster distance is computed for each sample.
			while (iterator.hasNext()) {
				
				exec.checkCanceled();
				
				DataRow row1 = iterator.next();
				ArrayList<Double> row1Doubles = row1.stream().map(x -> ((DoubleValue) x).getDoubleValue())
						.collect(Collectors.toCollection(ArrayList::new));
				RowIterator secondIterator = closestClusterTable.iterator();
				
				while (secondIterator.hasNext()) {
					DataRow row2 = secondIterator.next();
					ArrayList<Double> row2Doubles = row2.stream().map(x -> ((DoubleValue) x).getDoubleValue())
							.collect(Collectors.toCollection(ArrayList::new));
					if (!closestClusterSizeCounted) {
						closestClusterSize++;
					}
					currentClusterOutDistance += euclidianDistance(row1Doubles, row2Doubles);
				}
				closestClusterSizeCounted = true;
				currentClusterOutDistance = currentClusterOutDistance / closestClusterSize;
			}
			currentClusterOutDistance = currentClusterOutDistance / clusterSize;
			clusterOutDistance += currentClusterOutDistance;
			i++;
		}
		clusterInDistance = clusterInDistance / clusters.size();
		clusterOutDistance = clusterOutDistance / clusters.size();

		exec.checkCanceled();

		double sillhoetteCoefficient = (clusterOutDistance - clusterInDistance)
				/ Double.max(clusterInDistance, clusterOutDistance);

		DataCell sICell = new DoubleCell(sillhoetteCoefficient);

		outputContainer.addRowToTable(new DefaultRow(createRowKey((long) 1), sICell));

		outputContainer.close();

		closestCluster = null;
		clusterCentroids = null;
		clusters = null;

		return new BufferedDataTable[] { outputContainer.getTable() };
	}

	/**
	 * Calculates the closest cluster for each cluster.
	 * @return A HashMap where the String name of each cluster is mapped to the String name of the closest cluster.
	 */
	private HashMap<String, String> computeClosestClusters() {
		
		HashMap<String, String> closestClusters = new HashMap<String, String>();
		
		for (String cluster : clusterCentroids.keySet()) {
			
			Set<String> otherClusters = clusterCentroids.keySet().stream().collect(Collectors.toSet());
			otherClusters.remove(cluster);
			closestClusters.put(cluster, otherClusters.iterator().next());
			
			for (String otherCluster : otherClusters) {
				double currentDistance = euclidianDistance(clusterCentroids.get(cluster),
						clusterCentroids.get(closestClusters.get(cluster)));
				double otherClusterDistance = euclidianDistance(clusterCentroids.get(cluster),
						clusterCentroids.get(otherCluster));
				if (currentDistance > otherClusterDistance) {
					closestClusters.put(cluster, otherCluster);
				}
			}
		}
		return closestClusters;
	}

	/**
	 * Computes the Euclidian Distance for two points.
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
	 * Computes the cluster centroids for currently existing DataContainer in clusters HashMap. 
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
	 * Creates the DataTableSpec for the Data Containers that will be used for computing the Silhouette Coefficient.
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
	protected void reset() {
		
		clusters = new HashMap<String, DataContainer>();
		clusterCentroids = new HashMap<String, ArrayList<Double>>();
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
		colSpecs[0] = new DataColumnSpecCreator("Silhouette Coefficient", DoubleCell.TYPE).createSpec();
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
