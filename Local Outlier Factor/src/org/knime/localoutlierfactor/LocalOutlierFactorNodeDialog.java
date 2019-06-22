package org.knime.localoutlierfactor;

import org.knime.core.data.DoubleValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * <code>NodeDialog</code> for the "LocalOutlierFactor" Node.
 * This node computes the Local Outlier Factor for each point in a table and helps detect anomalous points.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Rytis Kumpa
 */
public class LocalOutlierFactorNodeDialog extends DefaultNodeSettingsPane {

    @SuppressWarnings("unchecked")
	protected LocalOutlierFactorNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentColumnFilter(
				new SettingsModelFilterString(LocalOutlierFactorNodeModel.CFGKEY_FILTER), 0, false, DoubleValue.class));
        
		final SettingsModelIntegerBounded m_numneigbors = new SettingsModelIntegerBounded(
				LocalOutlierFactorNodeModel.CFGKEY_NUMNEIGHBORS, LocalOutlierFactorNodeModel.DEFAULT_NUM, 1,
				Integer.MAX_VALUE);
		final String numNeigborsLabel = "Number of neigbors";		
		final int stepSize = 1;
		addDialogComponent(new DialogComponentNumber(m_numneigbors, numNeigborsLabel, stepSize));           
		
    }
}

