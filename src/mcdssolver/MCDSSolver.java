/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mcdssolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 *
 * @author xavierwoo
 */
public class MCDSSolver {

    HashSet<VNode> D_star;
    List<VNode> D_point_star;
    int num_edges_D_star;
    HashSet<VNode> D_plus;
    HashSet<VNode> D_minus;

    int iter_count = 0;
    int count_fail_improve=0;
    int best_f;
    int f;
    
    int tabu_length = 50;
    int base_tabu_length = 100;
    
    long start_time;

    final UndirectedGraph<VNode, DefaultEdge> graph = new SimpleGraph<VNode, DefaultEdge>(
            DefaultEdge.class);

    final Random rGen = new Random(0);
    final int LAMBDA = 1000;
    
    private VNode make_node(int nIndex) {
        for (VNode v : graph.vertexSet()) {
            if (v.index == nIndex) {
                return v;
            }
        }
        VNode v = new VNode(nIndex);
        graph.addVertex(v);
        return v;
    }

    public MCDSSolver(String instance) throws FileNotFoundException, IOException {
        BufferedReader in;

        in = new BufferedReader(new FileReader(instance));
        String line = in.readLine();
        String r[] = line.split(" ");
        int num_edge = Integer.parseInt(r[1]);

        for (int i = 0; i < num_edge; i++) {
            line = in.readLine();
            r = line.split(" ");
            VNode source = make_node(Integer.parseInt(r[0]));
            VNode sink = make_node(Integer.parseInt(r[1]));

            graph.addEdge(source, sink);
        }

        //System.out.println(graph);
    }

    private void initialization() {
        for (VNode v : graph.vertexSet()) {
            v.degree_to_D_star = graph.degreeOf(v);
        }
        D_star = new HashSet<>(graph.vertexSet());
        D_plus = new HashSet<>();
        D_minus = new HashSet<>();
        num_edges_D_star = graph.edgeSet().size();
    }

    private void move_out_D_star(VNode v) {
        D_star.remove(v);
        if (D_star.isEmpty()) {
            for (DefaultEdge e : graph.edgesOf(v)) {
                VNode u = graph.getEdgeSource(e);
                if (u == v) {
                    u = graph.getEdgeTarget(e);
                }
                u.degree_to_D_star--;
                D_plus.remove(u);
                D_minus.add(u);
            }
            num_edges_D_star = 0;
        } else {
            D_plus.add(v);
            for (DefaultEdge e : graph.edgesOf(v)) {
                VNode u = graph.getEdgeSource(e);
                if (u == v) {
                    u = graph.getEdgeTarget(e);
                }
                u.degree_to_D_star--;
                if (u.degree_to_D_star == 0 && !D_star.contains(u)) {
                    D_plus.remove(u);
                    D_minus.add(u);
                }
            }
            num_edges_D_star -= v.degree_to_D_star;
        }
    }

    private void articulation_points(VNode r) {
        r.visited = true;
        r.dep = depth;
        r.low = depth;
        depth++;

        for (DefaultEdge iter : graph.edgesOf(r)) {
            VNode tem = graph.getEdgeSource(iter);
            if (tem == r) {
                tem = graph.getEdgeTarget(iter);
            }

            if (D_star.contains(tem)) {
                if (!tem.visited) {
                    articulation_points(tem);
                    r.low = r.low < tem.low ? r.low : tem.low;
                    if (tem.low >= r.dep && r != root) {
                        r.is_articulation = true;
                    } else if (r == root) {
                        num_root_child++;
                    }
                } else {
                    r.low = r.low < tem.dep ? r.low : tem.dep;
                }
            }
        }
    }

    private int depth = 1;
    private int num_root_child = 0;
    private VNode root;

    private void determine_articulation_points() {
        depth = 1;
        num_root_child = 0;
        for (VNode v : D_star) {
            v.visited = false;
            v.dep = -1;
            v.low = -1;
            v.is_articulation = false;
        }
        VNode r = D_star.iterator().next();
        r.visited = true;
        root = r;
        articulation_points(r);
        if (num_root_child > 1) {
            r.is_articulation = true;
        }
    }

    private void reduce() {
        determine_articulation_points();
        D_point_star = new ArrayList<>(
                D_star.stream().filter((v) -> (v.is_articulation == false))
                .collect(Collectors.toList()));
        Collections.shuffle(D_point_star, rGen);
        VNode node_to_del = D_point_star.get(D_point_star.size() - 1);
        //D_point_star.remove(node_to_del);
        move_out_D_star(node_to_del);
        determine_articulation_points();
        D_point_star = new ArrayList<>(
                D_star.stream().filter((v) -> (v.is_articulation == false))
                .collect(Collectors.toList()));
    }

    private boolean is_validate(VNode minus_node, VNode add_node) {
        if (D_star.size() == 1) {
            return true;
        } else {
            return !(graph.containsEdge(minus_node, add_node)
                    && add_node.degree_to_D_star == 1);
        }
    }

    private Move find_move() {
        int min_delta = graph.vertexSet().size();
        int min_delta_tabu = graph.vertexSet().size();
        int count = 0;
        int count_tabu = 0;
        VNode minus_v = null;
        VNode add_v = null;
        VNode minus_v_tabu = null;
        VNode add_v_tabu = null;

        for (VNode a : D_point_star) {
            for (VNode b : D_plus) {
                if (!is_validate(a, b)) {
                    continue;
                }
                int delta = calc_delta(a, b);
                if (iter_count < b.tabu_tenur) {//if b is tabu
                    if (delta < min_delta_tabu) {
                        min_delta_tabu = delta;
                        minus_v_tabu = a;
                        add_v_tabu = b;
                        count_tabu = 1;
                    } else if (delta == min_delta_tabu
                            && rGen.nextInt(++count_tabu) == 0) {
                        min_delta_tabu = delta;
                        minus_v_tabu = a;
                        add_v_tabu = b;
                    }
                } else {//if b is not tabu
                    if (delta < min_delta) {
                        min_delta = delta;
                        minus_v = a;
                        add_v = b;
                        count = 1;
                    } else if (delta == min_delta
                            && rGen.nextInt(++count) == 0) {
                        min_delta = delta;
                        minus_v = a;
                        add_v = b;
                    }
                }
            }
        }

        Move move = null;
        if (minus_v_tabu != null && add_v_tabu != null
                && minus_v != null && add_v != null) {
            if (min_delta_tabu < min_delta && min_delta_tabu + D_minus.size() < best_f) {
                move = new Move(minus_v_tabu, add_v_tabu, min_delta_tabu);
            } else {
                move = new Move(minus_v, add_v, min_delta);
            }
        } else if (add_v == null && add_v_tabu != null) {
            move = new Move(minus_v_tabu, add_v_tabu, min_delta_tabu);
        } else if (add_v_tabu == null && add_v != null) {
            move = new Move(minus_v, add_v, min_delta);
        }
        return move;
    }

    private int calc_delta(VNode minus_v, VNode add_v) {
        int delta_v = 0;
        for (DefaultEdge e : graph.edgesOf(minus_v)) {
            VNode u = graph.getEdgeSource(e);
            if (u == minus_v) {
                u = graph.getEdgeTarget(e);
            }
            if (u.degree_to_D_star == 1 && !graph.containsEdge(u, add_v)) {
                delta_v++;
            }
        }

        for (DefaultEdge e : graph.edgesOf(add_v)) {
            VNode u = graph.getEdgeSource(e);
            if (u == add_v) {
                u = graph.getEdgeTarget(e);
            }
            if (u.degree_to_D_star == 0) {
                delta_v--;
            }
        }
        int delta_edge_num = - minus_v.degree_to_D_star + add_v.degree_to_D_star;
        if(graph.containsEdge(minus_v, add_v)){
            delta_edge_num--;
        }
        return delta_v;// * LAMBDA + delta_edge_num;
    }

    private void move_in_D_star(VNode v) {
        if(D_star.isEmpty()){
            D_minus.remove(v);
        }else{
            D_plus.remove(v);
        }
        
        D_star.add(v);


        for (DefaultEdge e : graph.edgesOf(v)) {
            VNode u = graph.getEdgeSource(e);
            if (u == v) {
                u = graph.getEdgeTarget(e);
            }
            u.degree_to_D_star++;
            if (u.degree_to_D_star == 1) {
                D_minus.remove(u);
                D_plus.add(u);
            }
        }
        num_edges_D_star += v.degree_to_D_star;

    }

    private void make_move(Move mv) {
        mv.minus_v.tabu_tenur = iter_count + rGen.nextInt(tabu_length)
                + base_tabu_length;
        move_out_D_star(mv.minus_v);
        move_in_D_star(mv.add_v);
        determine_articulation_points();
        D_point_star = new ArrayList<>(
                D_star.stream().filter((v) -> (v.is_articulation == false))
                .collect(Collectors.toList()));
        f += mv.delta;
    }

    private void prepare_LS() {
        iter_count = 0;
        best_f = graph.vertexSet().size();
        for (VNode v : graph.vertexSet()) {
            v.tabu_tenur = 0;
        }
    }

    private void local_search() throws FileNotFoundException {
        prepare_LS();
        f = D_minus.size();// * LAMBDA + num_edges_D_star;
        while (!D_minus.isEmpty()) {
            Move mv = find_move();
            make_move(mv);
            
            if(D_minus.size() != f){
                System.out.println("err");
            }
            
            if(f < best_f){
                best_f = f;
                count_fail_improve = 0;
            }else{
                count_fail_improve++;
            }
            
            //base_tabu_length = Math.min(50 + count_fail_improve, 200);
            iter_count++;
            
            if(iter_count%1000 == 0){
                System.out.println("iter: " +iter_count + "  objective: " + D_minus.size() + " best:"+best_f);
            }
        }
        write_result();
        check_solution();
        //System.out.println(D_star);
    }

    private void write_result() throws FileNotFoundException {
        String file_name = "result/res" + String.valueOf(D_star.size()) + ".txt";
        File file = new File(file_name);
        PrintWriter out = new PrintWriter(file);
        out.println("running time : " + (System.currentTimeMillis()-start_time) + "ms");
        out.println(D_star);
        
        out.close();
    }
    
    private void dfs(VNode r){
        r.visited = true;
        for(DefaultEdge e : graph.edgesOf(r)){
            VNode v = graph.getEdgeSource(e);
            if(v==r){
                v = graph.getEdgeTarget(e);
            }
            if(!v.visited && D_star.contains(v)){
                dfs(v);
            }
        }
    }
    
    private void check_solution(){
        //check domination
        for(VNode v : graph.vertexSet()){
            boolean is_dominated = false;
            if(!D_star.contains(v)){
                for(VNode d_v : D_star){
                    if(graph.containsEdge(v, d_v)){
                        is_dominated = true;
                        break;
                    }
                }
                if(is_dominated == false){
                    throw new UnsupportedOperationException("Vertex is not dominated:" + v);
                }
            }
        }
        
        //check connectivity
        for(VNode v : D_star){
            v.visited = false;
        }
        VNode r = D_star.iterator().next();
        dfs(r);
        for(VNode v : D_star){
            if(!v.visited ){
                throw new UnsupportedOperationException("D* is not connected!");
            }
        }
    }
    
    private void for_1_CDS() throws FileNotFoundException{
        for(VNode v : graph.vertexSet()){
            boolean all_dominated = true;
            for(VNode v_t : graph.vertexSet()){
                if(v == v_t){
                    continue;
                }
                if(!graph.containsEdge(v, v_t)){
                    all_dominated = false;
                    break;
                }
            }
            if(all_dominated){
                D_star.clear();
                D_star.add(v);
                write_result();
                return;
            }
        }
    }
    
    public void solve(int lower_bound) throws FileNotFoundException {
        initialization();
        start_time = System.currentTimeMillis();
        while (D_star.size() > lower_bound) {
            reduce();
            System.out.println("Solving "+D_star.size() +"-CDS...");
            if(D_star.size() == 1){
                for_1_CDS();
            }else{
                local_search();
            }
            
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // TODO code application logic here
        MCDSSolver solver = new MCDSSolver(args[0]);
        solver.solve(1);
    }

}
