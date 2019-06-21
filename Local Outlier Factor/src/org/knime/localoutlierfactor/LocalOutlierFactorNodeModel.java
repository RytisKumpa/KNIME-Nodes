package org.knime.localoutlierfactor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.AbstractCellFactory;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.container.SingleCellFactory;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.base.node.mine.cluster.hierarchical.distfunctions.EuclideanDist;
import org.knime.base.node.mine.mds.distances.EuclideanDistanceManager;
import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.KDTreeBuilder;
import org.knime.base.util.kdtree.NearestNeighbour;

/**
 * This is the model implementation of LocalOutlierFactor. This node computes
 * the Local Outlier Factor for each point in a table and helps detect anomalous
 * points.
 *
 * @author Rytis Kumpa
 */
public class LocalOutlierFactorNodeModel extends NodeModel {

	// the logger instance
	private static final NodeLogger logger = NodeLogger.getLogger(LocalOutlierFactorNodeModel.class);

	static final String CFGKEY_NUMNEIGHBORS = "Number of neighbors";
	static final String CFGKEY_FILTER = "Include columns";

	static final int DEFAULT_NUM = 30;

	private final SettingsModelIntegerBounded m_numneigbors = new SettingsModelIntegerBounded(CFGKEY_NUMNEIGHBORS,
			DEFAULT_NUM, 1, Integer.MAX_VALUE);

	private final SettingsModelFilterString m_filterString = new SettingsModelFilterString(CFGKEY_FILTER);

	/**
	 * Constructor for the node model.
	 */
	protected LocalOutlierFactorNodeModel() {

		// TODO one incoming port and one outgoing port is assumed
		super(1, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {

		// TODO do something here
		logger.info("Node Model Stub... this is not yet implemented !");

		DataTableSpec inSpec = inData[0].getDataTableSpec();

		BufferedDataTable inTable = inData[0];

		DataTableSpec outputSpec = configure(new DataTableSpec[] { inSpec })[0];

		BufferedDataContainer container = exec.createDataContainer(outputSpec);

		// List of columns selected for processing and their respective IDs
		List<String> includeList = m_filterString.getIncludeList();
		int[] includeListID = inSpec.columnsToIndices((String[]) includeList.stream().toArray(String[]::new));

		// Create K-D Tree for efficiently finding nearest neighbors;
		CloseableRowIterator inTableIterator = inTable.iterator();
		KDTreeBuilder<ArrayList<Double>> kdTree = new KDTreeBuilder<ArrayList<Double>>(includeList.size());
		
		while (inTableIterator.hasNext()) {
			
			exec.checkCanceled();
			
			DataRow currentRow = inTableIterator.next();
			ArrayList<Double> currentRowArrayList = new ArrayList<Double>();
			
			for (int idX : includeListID) {
				currentRowArrayList.add(((DoubleCell) currentRow.getCell(idX)).getDoubleValue());
			}
			
			kdTree.addPattern(currentRowArrayList.stream().mapToDouble(Double::doubleValue).toArray(),
					currentRowArrayList);
		}
		inTableIterator.close();

		KDTree<ArrayList<Double>> finalKDTree = kdTree.buildTree();

		inTableIterator = inTable.iterator();
		long i = 0;

		while (inTableIterator.hasNext()) {

			exec.checkCanceled();

			DataRow currentRow = inTableIterator.next();
			ArrayList<Double> currentRowArrayList = new ArrayList<Double>();

			for (int idX : includeListID) {
				currentRowArrayList.add(((DoubleCell) currentRow.getCell(idX)).getDoubleValue());
			}

			double[] currentRowDoubleArray = currentRowArrayList.stream().mapToDouble(Double::doubleValue).toArray();

			double currentLRD = localReachabilityDensity(finalKDTree, currentRowDoubleArray,
					m_numneigbors.getIntValue());

			List<NearestNeighbour<ArrayList<Double>>> nearestNeighbours = finalKDTree
					.getKNearestNeighbours(currentRowDoubleArray, m_numneigbors.getIntValue());

			double neigborLRD = 0.0;
			for (NearestNeighbour<ArrayList<Double>> neighbour : nearestNeighbours) {
				double[] neigborData = neighbour.getData().stream().mapToDouble(Double::doubleValue).toArray();
				neigborLRD += localReachabilityDensity(finalKDTree, neigborData, m_numneigbors.getIntValue());
			}

			double LOC = neigborLRD / (double) m_numneigbors.getIntValue() / currentLRD;

			ArrayList<DataCell> currentRowCells = currentRow.stream()
					.collect(Collectors.toCollection(ArrayList<DataCell>::new));
			currentRowCells.add(new DoubleCell(LOC));
			container.addRowToTable(new DefaultRow("RowKey_" + i++, currentRowCells));

		}

		container.close();
		BufferedDataTable out = container.getTable();
		return new BufferedDataTable[] { out };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
		// TODO Code executed on reset.
		// Models build during execute are cleared here.
		// Also data handled in load/saveInternals will be erased here.
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		DataTableSpec inSpec = inSpecs[0];
		DataColumnSpec newColumnSpec = new DataColumnSpecCreator("Local Outlier Factor", DoubleCell.TYPE).createSpec();
		DataTableSpec appendedSpec = new DataTableSpec(newColumnSpec);
		DataTableSpec outSpec = new DataTableSpec(inSpec, appendedSpec);

		return new DataTableSpec[] { outSpec };
	}

	private double localReachabilityDensity(KDTree<ArrayList<Double>> tree, double[] query, int numNeingbors) {

		double distance = 0.0;

		List<NearestNeighbour<ArrayList<Double>>> nearestNeighbours = tree.getKNearestNeighbours(query, numNeingbors);

		for (NearestNeighbour<ArrayList<Double>> neighbour : nearestNeighbours) {
			distance += neighbour.getDistance();
		}

		double localReachabilityDensity = 1 / (distance / (double) numNeingbors);

		return localReachabilityDensity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {

		// TODO save user settings to the config object.

		m_numneigbors.saveSettingsTo(settings);
		m_filterString.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {

		// TODO load (valid) settings from the config object.
		// It can be safely assumed that the settings are valided by the
		// method below.

		m_numneigbors.loadSettingsFrom(settings);
		m_filterString.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

		// TODO check if the settings could be applied to our model
		// e.g. if the count is in a certain range (which is ensured by the
		// SettingsModel).
		// Do not actually set any values of any member variables.

		m_numneigbors.validateSettings(settings);
		m_filterString.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

		// TODO load internal data.
		// Everything handed to output ports is loaded automatically (data
		// returned by the execute method, models loaded in loadModelContent,
		// and user settings set through loadSettingsFrom - is all taken care
		// of). Load here only the other internals that need to be restored
		// (e.g. data used by the views).

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {

		// TODO save internal models.
		// Everything written to output ports is saved automatically (data
		// returned by the execute method, models saved in the saveModelContent,
		// and user settings saved through saveSettingsTo - is all taken care
		// of). Save here only the other internals that need to be preserved
		// (e.g. data used by the views).

	}

}
