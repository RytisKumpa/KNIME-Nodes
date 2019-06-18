package org.knime.other;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnNameSelection;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentNumberEdit;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnName;
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;

/**
 * <code>NodeDialog</code> for the "DaviesBouldinIndex" Node.
 * This node computes the Davies-Bouldin index for evaluating clustering performance on a dataset.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Rytis Kumpa
 */
public class DaviesBouldinIndexNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring DaviesBouldinIndex node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
	@SuppressWarnings("unchecked")
	protected DaviesBouldinIndexNodeDialog() {
        super();
        addDialogComponent(new DialogComponentColumnFilter(
				new SettingsModelFilterString(DaviesBouldinIndexNodeModel.CFGKEY_FILTER), 0, false, DoubleValue.class));

		final String columnSelectLabel = "Column with cluster names:";
		addDialogComponent(new DialogComponentColumnNameSelection(
				new SettingsModelColumnName(DaviesBouldinIndexNodeModel.CFGKEY_CLUSTER, ""), columnSelectLabel, 0,
				StringValue.class));

		final String randomSamplingLabel = "Randomly sample a subset";
		final SettingsModelBoolean m_randomSampling = new SettingsModelBoolean(
				DaviesBouldinIndexNodeModel.CFGKEY_RANDOM, true);
		addDialogComponent(new DialogComponentBoolean(m_randomSampling, randomSamplingLabel));

		final String sampleSizeLabel = "Size of the sample:";
		final SettingsModelDoubleBounded m_sampleRate = new SettingsModelDoubleBounded(
				DaviesBouldinIndexNodeModel.CFGKEY_SAMPLERATE, 0.5, 0.001, 1.0);
		addDialogComponent(new DialogComponentNumber(m_sampleRate, sampleSizeLabel, 0.1));

		final String randomStateTitle = "Random seed:";
		final SettingsModelInteger m_randomState = new SettingsModelInteger(DaviesBouldinIndexNodeModel.CFGKEY_STATE,
				0);
		addDialogComponent(new DialogComponentNumberEdit(m_randomState, randomStateTitle));

		m_randomSampling.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent arg0) {
				if (m_randomSampling.getBooleanValue()) {
					m_sampleRate.setEnabled(true);
					m_randomState.setEnabled(true);
				} else {
					m_sampleRate.setEnabled(false);
					m_randomState.setEnabled(false);
				}

			}
		});  
    }
}

