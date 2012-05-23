package GP;

import TreeDistance.*;

import java.util.*;
import java.io.*;

import static java.lang.Math.*;


/*
 * Sample.java. This is the top-level class for doing sampling of
 * individuals and calculating syntactic and mutation-based distances
 * between them.
 *
 */

public class Sample {

    Random rng;
    int maxDepth;
    Language language;
    Mutation mutator;
    
    public Sample(int _maxDepth) {

        language = new Language(_maxDepth);
        rng = new Random();
        maxDepth = _maxDepth;
        mutator = new Mutation(language, maxDepth, rng);

    }

    // Given a list of trees, write out a matrix of distances between
    // all pairs, for many different types of distance.
    public void writeMatrices(ArrayList<String> trees, String codename) {

        String [] distanceNames = {
            "1STP", "NCD", "FVD",
            "NodeCount", "MinDepth", "MeanDepth", "MaxDepth",
            "Symmetry", "MeanFanout", "DiscreteMetric",
            "TED",
            "TAD0", "TAD1", "TAD2", "TAD3", "TAD4", "TAD5",
            "OVD"
        };

        HashMap<String, FileWriter> files = new HashMap<String, FileWriter>();
        try {
            // Open files
            for (String distance: distanceNames) {
                // write to ../results/<codename>/<distance>.dat
                String dirname = "../results/" + codename + "/";
                (new File(dirname)).mkdirs();
                String filename = dirname + distance + ".dat";
                files.put(distance, new FileWriter(filename)); 
            }

            // for every source tree
            for (String s: trees) {
                // for every destination tree
                for (String t: trees) {

                    // Get distances and write them out
                    HashMap<String, Double> distances = getDistances(s, t);
                    for (String distance: distanceNames) {
                        files.get(distance).write(distances.get(distance) + " ");
                    }
                }
                // write a newline
                for (String distance: distanceNames) {
                    files.get(distance).write("\n");
                }
            }

            // close files
            for (String distance: distanceNames) {
                files.get(distance).close();
            }
        
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }


    public void randomWalking(Boolean hillClimb) {
        int nStarts = 1000;
        int length = 1298;

        double oldfit = 0.0, newfit = 0.0;

        for (int i = 0; i < nStarts; i++) {
            ArrayList<String> walk = new ArrayList<String>();
            
            // start with a random node
            Tree tree = new Tree("x");
            mutator.grow(tree.getRoot(), maxDepth);
            if (hillClimb) {
                oldfit = tree.fitness();
            }
            String tt = tree.toString();
            // System.out.println("starting on " + tt);
            walk.add(tt);

            // perform a random walk
            int j = 1;
            int tries = 0;
            while (j <= length && tries < length) {

                tree = mutator.mutate(tree);
                if (hillClimb) {
                    newfit = tree.fitness();
                    if (newfit > oldfit) {
                        // if the new one is worse, throw it away and loop
                        tries += 1;
                        continue;
                    }
                    oldfit = newfit;
                }
                String ts = tree.toString();

                // go through previous steps in this walk and for each
                // one, save the number of steps from there to here.
                // But be careful to exclude loops using lastIndexOf().
                int prev = walk.lastIndexOf(ts);
                // add ts after getting the previous instance of it,
                // if any.
                walk.add(ts);
                // System.out.println("on mutation " + j + ", ts = " + ts + ", prev = " + prev);
                for (int k = prev + 1; k < j; k++) {
                    System.out.println(walk.get(k) + ":" + ts + ":" + (j - k));
                }
                
                j += 1;
            }
        }
    }
                


    // This does Metropolis-Hastings sampling as adapted for GP by
    // Vanneschi (see Vanneschi PhD thesis available online, p 130).
    // We run M-H multiple times, starting from the same "centre"
    // node, then return a list of all individuals encountered. FIXME
    // this needs testing in the new context.
    public ArrayList<String>
        sampleMetropolisHastings
        (Tree gamma_0, int npaths, int nsteps, boolean selection) {

        int ntries = 10;

        ArrayList<String> retval = new ArrayList<String>();

        retval.add(gamma_0.toString());

        float fgamma_0 = 0.0f;
        if (selection) {
            fgamma_0 = (float) (gamma_0.fitness());
        }

        for (int i = 0; i < npaths; i++) {
            Tree gamma_i = gamma_0;
            float fgamma_i = fgamma_0;

            for (int j = 0; j < nsteps; j++) {

                if (!selection) {
                    gamma_i = mutator.mutate(gamma_i);
                    retval.add(gamma_i.toString());

                } else {
                    // try up to ntries times to find a better
                    // individual. if we have to give up, then just
                    // keep the final one and loop.
                    for (int k = 0; true; k++) {
                        Tree delta = mutator.mutate(gamma_i);
                        float fdelta = (float) delta.fitness();
						double alpha;
						// FIXME maximise should be passed-in as a
						// parameter according to the problem.
						boolean maximise = false;
						if (maximise) {
							alpha = min(1.0f, fdelta / fgamma_i);
						} else {
							alpha = min(1.0f, fgamma_i / fdelta);
						}
                        if (rng.nextDouble() <= alpha || k == ntries) {
                            gamma_i = delta;
                            fgamma_i = fdelta;
                            retval.add(gamma_i.toString());
                            break;
                        }
                    }
                }
            }
        }

        return retval;
    }

    // Get every possible node (up to a given depth).
    public ArrayList<String> sampleComplete() {

        ArrayList<String> retval = new ArrayList<String>();

        AllTrees at = new AllTrees(language);
        ArrayList<Node> allTrees = at.generateEntireSpace(maxDepth);
        for (Node t: allTrees) {
            String ts = t.toStringAsTree();
            retval.add(ts);
        }

        return retval;
    }


    // A central method: give two trees (as strings), calculate
    // syntactic and operator-based distances, and return them
    // in a hash.
    public HashMap<String, Double> getDistances(String s, String t) {

        HashMap<String, Double> retval = new HashMap<String, Double>();
        
        Tree sTree = new Tree(s);
        Tree tTree = new Tree(t);

		double logCutoff = 10e-44;

        // One-shot transition probability.
        double transProb = mutator.transitionProbability(sTree, tTree);
        retval.put("1STP", transProb);

        // NCD
        double ncd = TreeDistance.getUniversalDistance(s.toString(), t.toString());
        retval.put("NCD", ncd);

        // FVD
        FeatureVectorDistance fvdObj = new
            FeatureVectorDistance(language.maxDepth, language.maxArity);
        double fvd = fvdObj.fvd(sTree, tTree);
        retval.put("FVD", fvd);
        FeatureVector sfv = new FeatureVector(sTree);
        FeatureVector tfv = new FeatureVector(tTree);
        retval.put("NodeCount", fvdObj.getNodeCount(sfv, tfv));
        retval.put("MinDepth", fvdObj.getMinDepth(sfv, tfv));
        retval.put("MeanDepth", fvdObj.getMeanDepth(sfv, tfv));
        retval.put("MaxDepth", fvdObj.getMaxDepth(sfv, tfv));
        retval.put("Symmetry", fvdObj.getSymmetry(sfv, tfv));
        retval.put("MeanFanout", fvdObj.getMeanFanout(sfv, tfv));
        retval.put("DiscreteMetric", fvdObj.getDiscreteMetricOnString(sfv, tfv));

        // TED FIXME could use the Augsten & Pawlik code instead?
        double ted = TreeDistance.getTreeEditDistance(sTree, tTree);
        retval.put("TED", ted);

        // TAD
        double tad[] = new double[6];
        for (int i = 0; i < 6; i++) {
            tad[i] = TreeDistance.getTreeAlignmentDistance(sTree, tTree, i);
            // System.out.println("TAD" + i + ": " + tad[i]);
            retval.put("TAD" + i, tad[i]);
        }

        // OVD
        double ovd = TreeDistance.getOverlapDistance(sTree, tTree);
        retval.put("OVD", ovd);

        return retval;
    }
        
    public static void main(String args[]) {

        if (args.length == 2 && args[0].equals("completeMatrices")) {
            int maxDepth = new Integer(args[1]);
            // write out the matrices of distances for the entire
            // space of given depth
            Sample sample = new Sample(maxDepth);
            sample.writeMatrices(sample.sampleComplete(), "depth_" + maxDepth);
        } else {
            System.out.println("Please read the source to see usage.");
        }
    }
}
