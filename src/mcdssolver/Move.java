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
public class Move {
    VNode minus_v;
    VNode add_v;
    int delta;
    
    protected Move(VNode v1, VNode v2, int d){
        minus_v = v1;
        add_v = v2;
        delta = d;
    }
}
