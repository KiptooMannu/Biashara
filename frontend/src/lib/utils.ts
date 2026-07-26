import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merges class names, with later Tailwind utilities winning over earlier ones of
 * the same kind. This is what lets a shadcn component's default classes be
 * overridden by a caller's className without specificity fights.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
