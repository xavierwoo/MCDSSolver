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
import java.util.Iterator;
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
    int count_fail_improve = 0;
    int best_f;
    int f;

    int tabu_length = 50;
    int base_tabu_length = 10;

    long start_time;

    final UndirectedGraph<VNode, DefaultEdge> graph = new SimpleGraph<VNode, DefaultEdge>(
            DefaultEdge.class);

    final Random rGen = new Random();
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

        determine_articulation_points();
        D_point_star = new ArrayList<>(
                D_star.stream().filter((v) -> (v.is_articulation == false))
                .collect(Collectors.toList()));

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
                    } else if (delta == min_delta_tabu) {
                        if (rGen.nextInt(count_tabu + 1) == 0) {
                            min_delta_tabu = delta;
                            minus_v_tabu = a;
                            add_v_tabu = b;

                        }
                        count_tabu++;
                    }
                } else {//if b is not tabu
                    if (delta < min_delta) {
                        min_delta = delta;
                        minus_v = a;
                        add_v = b;
                        count = 1;
                    } else if (delta == min_delta) {
                        if (rGen.nextInt(count + 1) == 0) {
                            minus_v = a;
                            add_v = b;
                            count++;
                        }
                        count++;
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
        return delta_v;
    }

    private void move_in_D_star(VNode v) {
        if(D_minus.contains(v)){
            D_minus.remove(v);
        }
        if(D_plus.contains(v)){
            D_plus.remove(v);
        }
        D_star.add(v);

        for (DefaultEdge e : graph.edgesOf(v)) {
            VNode u = graph.getEdgeSource(e);
            if (u == v) {
                u = graph.getEdgeTarget(e);
            }
            u.degree_to_D_star++;
            if (u.degree_to_D_star == 1 && !D_star.contains(u)) {
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
        f += mv.delta;
    }

    private void prepare_LS() {
        iter_count = 0;
        best_f = graph.vertexSet().size();
        for (VNode v : graph.vertexSet()) {
            v.tabu_tenur = 0;
        }
    }

    private void roll_back(HashSet<VNode> bak_D_star) {
        D_star.clear();
        D_plus.clear();
        D_minus.clear();
        D_minus.addAll(graph.vertexSet());
        for (VNode v : graph.vertexSet()) {
            v.degree_to_D_star = 0;
        }
        for (VNode v : bak_D_star) {
            D_minus.remove(v);
            D_plus.remove(v);
            D_star.add(v);
            for (DefaultEdge e : graph.edgesOf(v)) {
                VNode nv = graph.getEdgeSource(e);
                if (nv == v) {
                    nv = graph.getEdgeTarget(e);
                }
                if (!D_star.contains(nv)) {
                    D_minus.remove(nv);
                    D_plus.add(nv);
                }
                nv.degree_to_D_star++;
            }
        }
        determine_articulation_points();
        D_point_star = new ArrayList<>(
                D_star.stream().filter((v) -> (v.is_articulation == false))
                .collect(Collectors.toList()));
    }



    private void local_search() throws FileNotFoundException {
        prepare_LS();
        f = D_minus.size();// * LAMBDA + num_edges_D_star;
        best_f = f;
        HashSet<VNode> bak_D_star = new HashSet<>(D_star);

        final int base_strength = D_star.size() / 3;
        final int max_strength = D_star.size() - 1;

        int perturb_strength = base_strength;
        int perturb_count = 0;

        while (!D_minus.isEmpty()) {
            Move mv = find_move();

            //check_solution();
            if (D_minus.size() != f) {
                System.out.println("err");
            }

            if (mv.delta > 0 && f < best_f) {
                best_f = f;
                count_fail_improve = 0;
                bak_D_star.clear();
                bak_D_star.addAll(D_star);
                perturb_strength = base_strength;
                perturb_count = 0;
            } else if (f == best_f) {
                if (D_star.equals(bak_D_star)) {
                    perturb_strength = Math.min(perturb_strength + 1, max_strength);
                    //System.out.println("Strength boost  " + perturb_strength);
                } else {
                    perturb_strength = base_strength;
                    bak_D_star.clear();
                    bak_D_star.addAll(D_star);
                    //System.out.println("Strength shrank   "+ perturb_strength);
                }
                //count_fail_improve++;
            } else {
                count_fail_improve++;
            }



            make_move(mv);

            if (D_star.size() + D_plus.size() + D_minus.size() != graph.vertexSet().size()) {
                System.out.println("w");
            }

            //check_D_minus();
            if (D_minus.size() != f) {
                System.out.println("err");
            }

            iter_count++;

            if (iter_count % 1000 == 0) {
                System.out.println("iter: " + iter_count + "  objective: " + D_minus.size() + " best:" + best_f);
            }

            if (count_fail_improve > 300) {
                count_fail_improve = 0;
                perturb_count++;
                roll_back(bak_D_star);
                perturbation(perturb_strength);
                f = D_minus.size();

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
        out.println("running time : " + (System.currentTimeMillis() - start_time) + "ms");
        out.println(D_star);

        out.close();
    }

    private void dfs(VNode r) {
        r.visited = true;
        for (DefaultEdge e : graph.edgesOf(r)) {
            VNode v = graph.getEdgeSource(e);
            if (v == r) {
                v = graph.getEdgeTarget(e);
            }
            if (!v.visited && D_star.contains(v)) {
                dfs(v);
            }
        }
    }

    private void check_connectivity() {
        for (VNode v : D_star) {
            v.visited = false;
        }
        VNode r = D_star.iterator().next();
        dfs(r);
        for (VNode v : D_star) {
            if (!v.visited) {
                throw new UnsupportedOperationException("D* is not connected!");
            }
        }
    }

    private void check_solution() {
        //check domination
        for (VNode v : graph.vertexSet()) {
            boolean is_dominated = false;
            if (!D_star.contains(v)) {
                for (VNode d_v : D_star) {
                    if (graph.containsEdge(v, d_v)) {
                        is_dominated = true;
                        break;
                    }
                }
                if (is_dominated == false) {
                    throw new UnsupportedOperationException("Vertex is not dominated:" + v);
                }
            }
        }

        //check connectivity
        check_connectivity();
    }
    
    private VNode get_random_in_set(HashSet<VNode> vSet){
        int rI = rGen.nextInt(vSet.size());
        
        Iterator<VNode> iter =vSet.iterator();
        VNode rv=null;
        for(int i=0; i<=rI; i++){
            rv = iter.next();
        }
        return rv;
    }
    
    private Move random_mv(){
        VNode a, b;
        do{
            determine_articulation_points();
            D_point_star = new ArrayList<>(
                    D_star.stream().filter((v) -> (v.is_articulation == false))
                    .collect(Collectors.toList()));
            Collections.shuffle(D_point_star, rGen);
            a = D_point_star.get(0);
            b = get_random_in_set(D_plus);
            
        }while(!is_validate(a,b));
        
        return new Move(a,b,calc_delta(a,b));
    }
    
    private void perturbation(int p_length){
        for(int i=0; i<p_length; i++){
            
            Move mv = random_mv();
            make_move(mv);
            if (D_star.size() + D_plus.size() + D_minus.size() != graph.vertexSet().size()) {
                System.out.println("w");
            }
        }
    }
    

    private void for_1_CDS() throws FileNotFoundException {
        for (VNode v : graph.vertexSet()) {
            boolean all_dominated = true;
            for (VNode v_t : graph.vertexSet()) {
                if (v == v_t) {
                    continue;
                }
                if (!graph.containsEdge(v, v_t)) {
                    all_dominated = false;
                    break;
                }
            }
            if (all_dominated) {
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
            System.out.println("Solving " + D_star.size() + "-CDS...");
            if (D_star.size() == 1) {
                for_1_CDS();
            } else {
                local_search();
            }

        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // TODO code application logic here
        MCDSSolver solver = new MCDSSolver("instances/v200_d5.dat");
        solver.solve(26);
    }

}
