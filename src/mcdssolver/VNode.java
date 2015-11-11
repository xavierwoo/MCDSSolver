/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mcdssolver;

/**
 *
 * @author xavierwoo
 */
public class VNode {

    final int index;
    int degree_to_D_star=0;
    boolean is_articulation = false;
    public boolean visited = false;
    public int dep;
    public int low;
    public int tabu_tenur;

    public VNode(int i) {
        index = i;
    }

    @Override
    public String toString() {
        return String.valueOf(index);
    }

//    @Override
//    public boolean equals(Object o) {
//        VNode n = (VNode) o;
//        return n.index == this.index;
//    }
//
//    @Override
//    public int hashCode() {
//        int hash = 7;
//        hash = 79 * hash + this.index;
//        return hash;
//    }
}
