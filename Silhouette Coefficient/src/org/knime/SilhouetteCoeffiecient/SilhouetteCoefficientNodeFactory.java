package org.knime.SilhouetteCoeffiecient;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "SilhouetteCoefficient" Node.
 * This node computes the Silhouette Coefficient for the provided clustering result.
 *
 * @author Rytis Kumpa
 */
public class SilhouetteCoefficientNodeFactory 
        extends NodeFactory<SilhouetteCoefficientNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public SilhouetteCoefficientNodeModel createNodeModel() {
        return new SilhouetteCoefficientNodeModel();
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
    public NodeView<SilhouetteCoefficientNodeModel> createNodeView(final int viewIndex,
            final SilhouetteCoefficientNodeModel nodeModel) {
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
        return new SilhouetteCoefficientNodeDialog();
    }

}

