package org.knime.localoutlierfactor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.KDTreeBuilder;
import org.knime.base.util.kdtree.NearestNeighbour;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

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

	static final int DEFAULT_NUM = 15;

	private final SettingsModelIntegerBounded m_numneigbors = new SettingsModelIntegerBounded(CFGKEY_NUMNEIGHBORS,
			DEFAULT_NUM, 1, Integer.MAX_VALUE);

	private final SettingsModelFilterString m_filterString = new SettingsModelFilterString(CFGKEY_FILTER);

	protected LocalOutlierFactorNodeModel() {
		super(1, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {

		DataTableSpec inSpec = inData[0].getDataTableSpec();

		BufferedDataTable inTable = inData[0];
		
		if(inTable.getDataTableSpec().getColumnNames().length == 0) {
			throw new Exception("Empty tables not supported.");
		}

		DataTableSpec outputSpec = configure(new DataTableSpec[] { inSpec })[0];

		BufferedDataContainer container = exec.createDataContainer(outputSpec);
		
		// List of columns selected for processing and their respective IDs
		List<String> includeList = m_filterString.getIncludeList();
		int[] includeListID = inSpec.columnsToIndices((String[]) includeList.stream().toArray(String[]::new));

		// Create K-D Tree for efficiently finding nearest neighbors;
		CloseableRowIterator inTableIterator = inTable.iterator();
		KDTreeBuilder<ArrayList<Double>> kdTree = new KDTreeBuilder<ArrayList<Double>>(includeList.size());
		long i = 0;
		while (inTableIterator.hasNext()) {
			
			exec.checkCanceled();
			exec.setProgress(((double) i++) / (double) inTable.size() * 0.1, "Building KDTree.");
			DataRow currentRow = inTableIterator.next();
			ArrayList<Double> currentRowArrayList = new ArrayList<Double>();
			
			for (int idX : includeListID) {
				DataCell currentCell = currentRow.getCell(idX);
				if(!currentCell.isMissing()) {
					currentRowArrayList.add(((DoubleCell) currentCell).getDoubleValue());
				} else {
					throw new Exception("Missing values not supported.");
				}
			}
			
			kdTree.addPattern(currentRowArrayList.stream().mapToDouble(Double::doubleValue).toArray(),
					currentRowArrayList);
		}
		inTableIterator.close();
		
		KDTree<ArrayList<Double>> finalKDTree = kdTree.buildTree();

		inTableIterator = inTable.iterator();
		i = 0;

		// Iterate through each point and append the computed Local Outlier Factor.
		while (inTableIterator.hasNext()) {

			exec.checkCanceled();
			exec.setProgress(0.1 + (double) i / inTable.size() * 0.9 , "Processing row: " + i);

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

			double LOF = neigborLRD / (double) m_numneigbors.getIntValue() / currentLRD;

			ArrayList<DataCell> currentRowCells = currentRow.stream()
					.collect(Collectors.toCollection(ArrayList<DataCell>::new));
			currentRowCells.add(new DoubleCell(LOF));
			container.addRowToTable(new DefaultRow("RowKey_" + i++, currentRowCells));
		}
		
		inTableIterator.close();
		finalKDTree = null;
		container.close();
		BufferedDataTable out = container.getTable();
		return new BufferedDataTable[] { out };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
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

	/**
	 * Computes the local reachability density for the particular query.
	 * @param tree The KDTree used to retrieve the nearest neighbors.
	 * @param query The point of which nearest neighbors are queried.
	 * @param numNeingbors The number of neighbors the point will be compared to.
	 * @return The double value of the computed value.
	 */
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
		
		m_numneigbors.saveSettingsTo(settings);
		m_filterString.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		
		m_numneigbors.loadSettingsFrom(settings);
		m_filterString.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

		m_numneigbors.validateSettings(settings);
		m_filterString.validateSettings(settings);

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
