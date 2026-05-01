package org.esa.snap.idepix.core.util;

import org.esa.snap.core.datamodel.Product;

/**
 * Container for a pair of name and Product to write an intermediate instead of the final output of a complex operator.
 * Usage:
 * A parameter of the complex operator selects the breakpoint by setting the name in this singleton.
 * Where the intermediate product of an embedded operator is assigned the respective code checks the name
 * in the breakpoint and sets the product if the name is the one for that intermediate.
 * The complex operator exchanges its target product with the one of the breakpoint.
 *
 * @author Martin Boettcher
 */
public class BreakpointOutput {
    private static BreakpointOutput instance;
    public static BreakpointOutput getInstance() {
        if (instance == null) {
            instance = new BreakpointOutput();
        }
        return instance;
    }

    private String name;

    private Product product;

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
