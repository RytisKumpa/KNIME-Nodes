package org.knime.localoutlierfactor;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "LocalOutlierFactor" Node.
 * This node computes the Local Outlier Factor for each point in a table and helps detect anomalous points.
 *
 * @author Rytis Kumpa
 */
public class LocalOutlierFactorNodeFactory 
        extends NodeFactory<LocalOutlierFactorNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalOutlierFactorNodeModel createNodeModel() {
        return new LocalOutlierFactorNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<LocalOutlierFactorNodeModel> createNodeView(final int viewIndex,
            final LocalOutlierFactorNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new LocalOutlierFactorNodeDialog();
    }

}

