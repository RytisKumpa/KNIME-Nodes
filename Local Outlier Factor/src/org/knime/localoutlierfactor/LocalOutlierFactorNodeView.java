package org.knime.localoutlierfactor;

import org.knime.core.node.NodeView;

/**
 * <code>NodeView</code> for the "LocalOutlierFactor" Node.
 * This node computes the Local Outlier Factor for each point in a table and helps detect anomalous points.
 *
 * @author Rytis Kumpa
 */
public class LocalOutlierFactorNodeView extends NodeView<LocalOutlierFactorNodeModel> {

    /**
     * Creates a new view.
     * 
     * @param nodeModel The model (class: {@link LocalOutlierFactorNodeModel})
     */
    protected LocalOutlierFactorNodeView(final LocalOutlierFactorNodeModel nodeModel) {
        super(nodeModel);

        // TODO instantiate the components of the view here.

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {

        // TODO retrieve the new model from your nodemodel and 
        // update the view.
        LocalOutlierFactorNodeModel nodeModel = 
            (LocalOutlierFactorNodeModel)getNodeModel();
        assert nodeModel != null;
        
        // be aware of a possibly not executed nodeModel! The data you retrieve
        // from your nodemodel could be null, emtpy, or invalid in any kind.
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {
    
        // TODO things to do when closing the view
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {

        // TODO things to do when opening the view
    }

}

