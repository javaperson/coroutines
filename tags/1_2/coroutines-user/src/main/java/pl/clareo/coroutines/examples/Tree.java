package pl.clareo.coroutines.examples;

import static pl.clareo.coroutines.user.Coroutines._;
import static pl.clareo.coroutines.user.Coroutines.yield;

import java.util.Iterator;

import pl.clareo.coroutines.user.CoIterator;
import pl.clareo.coroutines.user.Coroutine;

public class Tree<T extends Comparable<T>> {

    public static void main(String[] args) {
        Tree<Integer> tree1 = new Tree<Integer>(1);
        tree1.insert(8);
        tree1.insert(6);
        tree1.insert(3);
        tree1.insert(9);
        Tree<Integer> tree2 = new Tree<Integer>(6);
        tree2.insert(1);
        tree2.insert(3);
        tree2.insert(8);
        tree2.insert(9);
        System.out.println("same fringe?: " + Tree.sameFringe(tree1, tree2));
        Tree<Integer> tree3 = new Tree<Integer>(1);
        tree3.insert(10);
        tree3.insert(2);
        tree3.insert(9);
        tree3.insert(4);
        System.out.println("same fringe?: " + Tree.sameFringe(tree1, tree3));
    }

    public static <T extends Comparable<T>> boolean sameFringe(Tree<T> t1, Tree<T> t2) {
        Iterator<T> t1Leaves = t1.leaves(t1.root).each().iterator();
        Iterator<T> t2Leaves = t2.leaves(t2.root).each().iterator();
        while (t1Leaves.hasNext() && t2Leaves.hasNext()) {
            if (t1Leaves.next().compareTo(t2Leaves.next()) != 0) {
                return false;
            }
        }
        return !t1Leaves.hasNext() && !t2Leaves.hasNext();
    }

    private TreeNode root;

    public Tree(T root) {
        this.root = new TreeNode(root);
    }

    public Iterable<T> inorder() {
        return inorder(root).each();
    }

    @Coroutine
    private CoIterator<T, Void> inorder(TreeNode t) {
        if (t != null) {
            for (T x : inorder(t.left).each()) {
                yield(x);
            }
            yield(t.key);
            for (T x : inorder(t.right).each()) {
                yield(x);
            }
        }
        return _();
    }

    public void insert(T value) {
        insert(root, value);
    }

    private void insert(TreeNode node, T value) {
        if (value.compareTo(node.key) < 0) {
            if (node.left != null) {
                insert(node.left, value);
            } else {
                node.left = new TreeNode(value);
            }
        } else {
            if (node.right != null) {
                insert(node.right, value);
            } else {
                node.right = new TreeNode(value);
            }
        }
    }

    @Coroutine
    private CoIterator<T, Void> leaves(TreeNode t) {
        if (t.isLeaf()) {
            yield(t.key);
        } else {
            if (t.left != null) {
                for (T x : leaves(t.left).each()) {
                    yield(x);
                }
            }
            if (t.right != null) {
                for (T x : leaves(t.right).each()) {
                    yield(x);
                }
            }
        }
        return _();
    }

    private class TreeNode {

        final T  key;
        TreeNode left;
        TreeNode right;

        public TreeNode(T key) {
            this.key = key;
        }

        public boolean isLeaf() {
            return left == null && right == null;
        }
    }
}
